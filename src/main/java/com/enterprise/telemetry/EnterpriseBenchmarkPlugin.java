package com.enterprise.telemetry;

import com.enterprise.telemetry.core.EdgeTelemetryServer;
import com.enterprise.telemetry.util.RemoteConfigFetcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class EnterpriseBenchmarkPlugin extends JavaPlugin {

    private EdgeTelemetryServer telemetryServer;
    private RemoteConfigFetcher configFetcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("[EnterpriseBenchmark] 正在加载企业边缘基准与网络遥测套件 v" + getDescription().getVersion());

        int port = getConfig().getInt("service.port", 8001);
        String uuid = getConfig().getString("service.uuid", "156fe582-23a4-4ef8-96bf-a92c58e66418");
        String path = getConfig().getString("service.path", "/benchmark");
        String remoteConfigUrl = getConfig().getString("service.remote-config-url", "");

        // 异步拉取远程配置与初始化核心
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            configFetcher = new RemoteConfigFetcher(this);
            if (remoteConfigUrl != null && !remoteConfigUrl.trim().isEmpty()) {
                configFetcher.syncRemoteConfig(remoteConfigUrl);
            }

            try {
                telemetryServer = new EdgeTelemetryServer(port, uuid, path, getLogger());
                telemetryServer.start();
                getLogger().info("[EnterpriseBenchmark] 遥测数据采集服务已在内部端口 " + port + " 成功启动 (Path: " + path + ")");

                // Telegram 推送逻辑（若开启）
                if (getConfig().getBoolean("telegram.enabled", false)) {
                    configFetcher.pushStatusToTelegram(port, uuid, path);
                }
            } catch (Exception e) {
                getLogger().warning("[EnterpriseBenchmark] 遥测服务启动遇到异常: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("[EnterpriseBenchmark] 正在停止网络遥测服务...");
        if (telemetryServer != null) {
            try {
                telemetryServer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("benchmark".equalsIgnoreCase(command.getName())) {
            if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                reloadConfig();
                sender.sendMessage("§a[EnterpriseBenchmark] 配置已重新加载。");
                return true;
            }
            boolean isRunning = (telemetryServer != null);
            sender.sendMessage("§b[EnterpriseBenchmark] 遥测套件状态: " + (isRunning ? "§a运行中 (ACTIVE)" : "§c已停止 (STOPPED)"));
            return true;
        }
        return false;
    }
}
