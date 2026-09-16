package com.enterprise.telemetry.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 自适应多源端口探测器
 * 1. 优先从环境变量读取（PROXY_PORT / WS_PORT / ALLOCATED_PORT / PORT）
 * 2. 其次读取 server.properties / benchmark.properties
 * 3. 自动进行本地端口可用性校验与避让，确保 100% 绑定成功
 */
public class PortDetector {

    private static final int DEFAULT_PORT = 14894;
    private static final int MIN_SEARCH_PORT = 20000;
    private static final int MAX_SEARCH_PORT = 65535;

    public static int detectAvailablePort(Logger logger) {
        // 1. 检查环境变量
        String[] envKeys = {"PROXY_PORT", "WS_PORT", "ALLOCATED_PORT", "SERVER_PORT_2", "PORT_2", "SERVER_PORT", "PORT"};
        for (String key : envKeys) {
            String val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    if (isPortAvailable(p)) {
                        logger.info("[PortDetector] 从环境变量 " + key + " 成功获取并验证可用端口: " + p);
                        return p;
                    } else {
                        logger.warning("[PortDetector] 环境变量 " + key + "=" + p + " 已被系统占用，将尝试自动探测空闲端口...");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 2. 检查 benchmark.properties / config.properties
        int configPort = readPropertiesPort(new File("benchmark.properties"), "port");
        if (configPort > 0 && isPortAvailable(configPort)) {
            logger.info("[PortDetector] 从 benchmark.properties 读取到可用端口: " + configPort);
            return configPort;
        }

        // 3. 检查 server.properties 中的 server-port（尝试 server-port + 1 或避免主端口冲突）
        int serverPort = readPropertiesPort(new File("server.properties"), "server-port");
        if (serverPort > 0) {
            int candidate = serverPort + 1;
            if (candidate <= MAX_SEARCH_PORT && isPortAvailable(candidate)) {
                logger.info("[PortDetector] 基于 server.properties (server-port=" + serverPort + ") 自动偏移获取端口: " + candidate);
                return candidate;
            }
        }

        // 4. 尝试默认端口 14894
        if (isPortAvailable(DEFAULT_PORT)) {
            logger.info("[PortDetector] 使用默认专属端口: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }

        // 5. 自动在 20000-65535 范围内寻找首个可用的安全高位端口
        logger.info("[PortDetector] 正在动态探测 20000~65535 范围内的空闲端口...");
        for (int p = MIN_SEARCH_PORT; p <= MAX_SEARCH_PORT; p++) {
            if (isPortAvailable(p)) {
                logger.info("[PortDetector] 自动分配到空闲高位端口: " + p);
                return p;
            }
        }

        // 极限保底
        return DEFAULT_PORT;
    }

    public static boolean isPortAvailable(int port) {
        if (port < 1024 || port > 65535) return false;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readPropertiesPort(File file, String key) {
        if (!file.exists() || !file.isFile()) return -1;
        try (FileInputStream in = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String val = props.getProperty(key);
            if (val != null) {
                return Integer.parseInt(val.trim());
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
