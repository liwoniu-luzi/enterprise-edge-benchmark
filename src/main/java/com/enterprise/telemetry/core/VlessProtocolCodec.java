package com.enterprise.telemetry.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * 协议解析与编解码组件 (VLESS v0 协议纯 Java 实现)
 */
public class VlessProtocolCodec {

    public static class TargetDestination {
        public byte command;
        public String host;
        public int port;
        public byte[] payload;
    }

    /**
     * 将 36 字符标准 UUID 字符串或 32 位 Hex 转换为 16 字节 byte[]
     */
    public static byte[] uuidToBytes(String uuidStr) {
        if (uuidStr == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        String clean = uuidStr.trim().replace("-", "");
        if (clean.length() != 32) {
            throw new IllegalArgumentException("Invalid UUID format: " + uuidStr);
        }
        UUID uuid = UUID.fromString(clean.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        ));
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    /**
     * 解析 VLESS 请求包头并提取目标地址、端口和后续负载
     */
    public static TargetDestination parseRequest(byte[] data, byte[] expectedUuidBytes) throws Exception {
        if (data == null || data.length < 24) {
            throw new IllegalArgumentException("Payload too short for VLESS header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte version = buffer.get();
        if (version != 0) {
            throw new IllegalArgumentException("Unsupported VLESS version: " + version);
        }

        byte[] incomingUuid = new byte[16];
        buffer.get(incomingUuid);
        if (!Arrays.equals(incomingUuid, expectedUuidBytes)) {
            throw new SecurityException("UUID validation failed");
        }

        // Addons protobuf length
        int addonsLen = buffer.get() & 0xFF;
        if (addonsLen > 0) {
            buffer.position(buffer.position() + addonsLen);
        }

        // Command: 0x01 (TCP), 0x02 (UDP), 0x03 (Mux)
        byte command = buffer.get();

        // 2 bytes: Port (Big Endian)
        int port = buffer.getShort() & 0xFFFF;

        // 1 byte: Address Type
        byte addrType = buffer.get();
        String targetHost;

        if (addrType == 0x01) { // IPv4
            byte[] ipv4 = new byte[4];
            buffer.get(ipv4);
            targetHost = (ipv4[0] & 0xFF) + "." + (ipv4[1] & 0xFF) + "." + (ipv4[2] & 0xFF) + "." + (ipv4[3] & 0xFF);
        } else if (addrType == 0x02) { // Domain
            int domainLen = buffer.get() & 0xFF;
            byte[] domainBytes = new byte[domainLen];
            buffer.get(domainBytes);
            targetHost = new String(domainBytes, StandardCharsets.UTF_8);
        } else if (addrType == 0x03) { // IPv6
            byte[] ipv6 = new byte[16];
            buffer.get(ipv6);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i += 2) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02x%02x", ipv6[i], ipv6[i + 1]));
            }
            targetHost = sb.toString();
        } else {
            throw new IllegalArgumentException("Unknown address type: " + addrType);
        }

        // 提取剩余的初始 Payload
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        TargetDestination dest = new TargetDestination();
        dest.command = command;
        dest.host = targetHost;
        dest.port = port;
        dest.payload = payload;
        return dest;
    }

    /**
     * 生成 VLESS 响应头 (Version 0x00, AddonsLen 0x00)
     */
    public static byte[] createResponseHeader() {
        return new byte[]{0x00, 0x00};
    }
}
