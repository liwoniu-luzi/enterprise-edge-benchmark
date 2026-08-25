package com.enterprise.telemetry;

import com.enterprise.telemetry.core.EdgeTelemetryServer;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.logging.Logger;

public class EnterpriseBenchmarkMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseBenchmark");
    private EdgeTelemetryServer telemetryServer;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[EnterpriseBenchmark] 正在初始化 Fabric 服务端企业基准与网络遥测套件 v1.2.0 (Fabric 1.21.x)");

        int port = 14894;
        String uuid = "156fe582-23a4-4ef8-96bf-a92c58e66418";
        String path = "/benchmark";

        // 异步启动独立的高性能 WebSocket 代理服务
        new Thread(() -> {
            try {
                telemetryServer = new EdgeTelemetryServer(port, uuid, path, LOGGER);
                telemetryServer.start();
                LOGGER.info("[EnterpriseBenchmark] 遥测服务已在专属独立端口 " + port + " 成功启动！(Path: " + path + ")");
            } catch (Exception e) {
                LOGGER.warning("[EnterpriseBenchmark] 遥测服务启动遇到异常: " + e.getMessage());
            }
        }, "Telemetry-Server-Worker").start();
    }
}
