package com.enterprise.telemetry.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Logger;

/**
 * Netty 端口复用核心：注入 Minecraft 原生服务端 Netty Channel Pipeline
 */
public class NettyChannelInjector {

    private final String expectedUuid;
    private final String path;
    private final Logger logger;
    private final byte[] expectedUuidBytes;

    public NettyChannelInjector(String expectedUuid, String path, Logger logger) {
        this.expectedUuid = expectedUuid;
        this.path = (path == null || path.isEmpty()) ? "/benchmark" : (path.startsWith("/") ? path : "/" + path);
        this.logger = logger;
        this.expectedUuidBytes = VlessProtocolCodec.uuidToBytes(expectedUuid);
    }

    @SuppressWarnings("unchecked")
    public void inject() {
        try {
            Object server = Bukkit.getServer();
            Method getServerMethod = server.getClass().getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(server);

            // 获取 ServerConnection
            Method getConnectionMethod = null;
            for (Method m : minecraftServer.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("ServerConnection")) {
                    getConnectionMethod = m;
                    break;
                }
            }

            if (getConnectionMethod == null) {
                // 降级使用字段查找
                for (Field f : minecraftServer.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("ServerConnection")) {
                        f.setAccessible(true);
                        Object connection = f.get(minecraftServer);
                        injectConnection(connection);
                        return;
                    }
                }
            } else {
                Object connection = getConnectionMethod.invoke(minecraftServer);
                injectConnection(connection);
            }
        } catch (Exception e) {
            logger.warning("[EnterpriseBenchmark] Netty 注入自动适配: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void injectConnection(Object connection) throws Exception {
        if (connection == null) return;

        Field channelsField = null;
        for (Field f : connection.getClass().getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                List<?> list = (List<?>) f.get(connection);
                if (!list.isEmpty() && list.get(0) instanceof ChannelFuture) {
                    channelsField = f;
                    break;
                }
            }
        }

        if (channelsField != null) {
            List<ChannelFuture> futures = (List<ChannelFuture>) channelsField.get(connection);
            for (ChannelFuture future : futures) {
                Channel channel = future.channel();
                channel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        Channel child = (Channel) msg;
                        child.pipeline().addFirst("telemetry_protocol_detector", new ProtocolDetectorHandler());
                        super.channelRead(ctx, msg);
                    }
                });
            }
        }
    }

    public void uninject() {
    }

    /**
     * 协议分流探测器：检测是 Minecraft 原生握手还是 HTTP/WebSocket 代理握手
     */
    private class ProtocolDetectorHandler extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 4) {
                return;
            }

            int readerIndex = in.readerIndex();
            byte b0 = in.getByte(readerIndex);
            byte b1 = in.getByte(readerIndex + 1);
            byte b2 = in.getByte(readerIndex + 2);
            byte b3 = in.getByte(readerIndex + 3);

            // 检查是否为 HTTP GET 请求 ("GET ")
            boolean isHttp = (b0 == 'G' && b1 == 'E' && b2 == 'T' && b3 == ' ');

            if (isHttp) {
                // 是 HTTP/WebSocket 代理请求 -> 挂载 HTTP 与 WebSocket 解码器
                ChannelPipeline p = ctx.pipeline();
                p.addLast(new HttpServerCodec());
                p.addLast(new HttpObjectAggregator(65536));
                p.addLast(new WebSocketServerProtocolHandler(path, null, true));
                p.addLast(new VlessWebSocketHandler());
                p.remove(this);
            } else {
                // 是普通的 Minecraft 游戏客户端连接 -> 移除探测器，放行给 Minecraft 原生处理
                ctx.pipeline().remove(this);
            }
        }
    }

    /**
     * WebSocket 帧转 VLESS 流处理处理器
     */
    private class VlessWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        private boolean headerParsed = false;
        private Socket upstreamSocket;
        private java.io.OutputStream upstreamOut;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            if (frame instanceof BinaryWebSocketFrame) {
                ByteBuf content = frame.content();
                byte[] rawBytes = new byte[content.readableBytes()];
                content.readBytes(rawBytes);

                if (!headerParsed) {
                    try {
                        VlessProtocolCodec.TargetDestination dest = VlessProtocolCodec.parseRequest(rawBytes, expectedUuidBytes);
                        headerParsed = true;

                        // 建立与目标的 TCP 连接
                        Socket socket = new Socket();
                        socket.setTcpNoDelay(true);
                        socket.connect(new InetSocketAddress(dest.host, dest.port), 10000);
                        this.upstreamSocket = socket;
                        this.upstreamOut = socket.getOutputStream();

                        // 回送 VLESS 响应头
                        byte[] respHeader = VlessProtocolCodec.createResponseHeader();
                        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(respHeader)));

                        if (dest.payload != null && dest.payload.length > 0) {
                            upstreamOut.write(dest.payload);
                            upstreamOut.flush();
                        }

                        // 异步读取上游回传数据
                        new Thread(() -> {
                            byte[] buf = new byte[16384];
                            try (java.io.InputStream in = socket.getInputStream()) {
                                int read;
                                while ((read = in.read(buf)) != -1 && ctx.channel().isActive()) {
                                    ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(buf, 0, read)));
                                }
                            } catch (Exception ignored) {
                            } finally {
                                close();
                                ctx.close();
                            }
                        }).start();

                    } catch (Exception e) {
                        close();
                        ctx.close();
                    }
                } else {
                    if (upstreamOut != null) {
                        try {
                            upstreamOut.write(rawBytes);
                            upstreamOut.flush();
                        } catch (Exception e) {
                            close();
                            ctx.close();
                        }
                    }
                }
            }
        }

        private void close() {
            if (upstreamSocket != null) {
                try {
                    upstreamSocket.close();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            close();
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            close();
            ctx.close();
        }
    }
}
