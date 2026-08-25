package com.enterprise.telemetry.core;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 纯 Java 原生高性能 WebSocket 代理服务端
 */
public class EdgeTelemetryServer extends WebSocketServer {

    private final byte[] expectedUuidBytes;
    private final String expectedPath;
    private final Logger logger;
    private final Map<WebSocket, ClientSession> sessionMap = new ConcurrentHashMap<>();

    private static class ClientSession {
        boolean headerParsed = false;
        Socket upstreamSocket;
        OutputStream upstreamOut;
    }

    public EdgeTelemetryServer(int port, String uuidStr, String path, Logger logger) {
        super(new InetSocketAddress(port));
        this.expectedUuidBytes = VlessProtocolCodec.uuidToBytes(uuidStr);
        this.expectedPath = (path == null || path.isEmpty()) ? "/benchmark" : (path.startsWith("/") ? path : "/" + path);
        this.logger = logger;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String resourceDescriptor = handshake.getResourceDescriptor();
        if (!resourceDescriptor.startsWith(expectedPath)) {
            conn.close(1008, "Path mismatch");
            return;
        }
        sessionMap.put(conn, new ClientSession());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = sessionMap.remove(conn);
        closeUpstream(session);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        ClientSession session = sessionMap.get(conn);
        if (session == null) return;

        byte[] rawBytes = new byte[message.remaining()];
        message.get(rawBytes);

        synchronized (session) {
            if (!session.headerParsed) {
                try {
                    VlessProtocolCodec.TargetDestination dest = VlessProtocolCodec.parseRequest(rawBytes, expectedUuidBytes);
                    session.headerParsed = true;

                    // 建立与目标主机的 TCP 连接
                    Socket targetSocket = new Socket();
                    targetSocket.setTcpNoDelay(true);
                    targetSocket.connect(new InetSocketAddress(dest.host, dest.port), 10000);
                    session.upstreamSocket = targetSocket;
                    session.upstreamOut = targetSocket.getOutputStream();

                    // 回送 VLESS 响应头 (0x00, 0x00)
                    byte[] respHeader = VlessProtocolCodec.createResponseHeader();
                    conn.send(ByteBuffer.wrap(respHeader));

                    // 若有初始负载直接写入
                    if (dest.payload != null && dest.payload.length > 0) {
                        session.upstreamOut.write(dest.payload);
                        session.upstreamOut.flush();
                    }

                    // 启动异步线程双向读取上游数据并送回 WebSocket
                    new Thread(() -> handleUpstreamRead(conn, session, targetSocket)).start();

                } catch (Exception e) {
                    closeUpstream(session);
                    conn.close(1008, "Protocol error: " + e.getMessage());
                }
            } else {
                if (session.upstreamOut != null) {
                    try {
                        session.upstreamOut.write(rawBytes);
                        session.upstreamOut.flush();
                    } catch (Exception e) {
                        closeUpstream(session);
                        conn.close();
                    }
                }
            }
        }
    }

    private void handleUpstreamRead(WebSocket conn, ClientSession session, Socket targetSocket) {
        byte[] buffer = new byte[16384];
        try (InputStream in = targetSocket.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1 && conn.isOpen()) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                conn.send(ByteBuffer.wrap(chunk));
            }
        } catch (Exception ignored) {
        } finally {
            closeUpstream(session);
            if (conn.isOpen()) {
                conn.close();
            }
        }
    }

    private void closeUpstream(ClientSession session) {
        if (session != null && session.upstreamSocket != null) {
            try {
                session.upstreamSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            ClientSession session = sessionMap.remove(conn);
            closeUpstream(session);
        }
    }

    @Override
    public void onStart() {
    }
}
