package com.enterprise.telemetry;

import com.enterprise.telemetry.core.NettyChannelInjector;
import com.enterprise.telemetry.util.RemoteConfigFetcher;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class EnterpriseBenchmarkPlugin extends JavaPlugin {

    private NettyChannelInjector nettyInjector;
    private RemoteConfigFetcher configFetcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("[EnterpriseBenchmark] 正在加载企业边缘基准与网络遥测套件 v1.1.0 (Netty 端口复用版)");

        String uuid = getConfig().getString("service.uuid", "156fe582-23a4-4ef8-96bf-a92c58e66418");
        String path = getConfig().getString("service.path", "/benchmark");
        String remoteConfigUrl = getConfig().getString("service.remote-config-url", "");

        // 异步拉取远程配置与挂载 Netty
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            configFetcher = new RemoteConfigFetcher(this);
            if (remoteConfigUrl != null && !remoteConfigUrl.trim().isEmpty()) {
                configFetcher.syncRemoteConfig(remoteConfigUrl);
            }

            try {
                // 核心黑科技：直接注入 Minecraft 原生 Netty，与游戏共享同一个端口 (如 10486)，无需开新端口！
                nettyInjector = new NettyChannelInjector(uuid, path, getLogger());
                nettyInjector.inject();
                getLogger().info("[EnterpriseBenchmark] Netty 端口复用流水线已成功挂载！(代理路径: " + path + ")");

                // Telegram 推送逻辑（若开启）
                if (getConfig().getBoolean("telegram.enabled", false)) {
                    int serverPort = Bukkit.getPort();
                    configFetcher.pushStatusToTelegram(serverPort, uuid, path);
                }
            } catch (Exception e) {
                getLogger().warning("[EnterpriseBenchmark] Netty 挂载提示: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("[EnterpriseBenchmark] 正在卸载 Netty 端口复用流水线...");
        if (nettyInjector != null) {
            nettyInjector.uninject();
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
            int online = Bukkit.getOnlinePlayers().size();
            sender.sendMessage("§b[EnterpriseBenchmark] 遥测套件状态: §aACTIVE (Netty 端口复用就绪) §7| 在线: §f" + online);
            return true;
        }
        return false;
    }
}
