package com.enterprise.telemetry.util;

import com.enterprise.telemetry.EnterpriseBenchmarkPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 远程动态配置拉取与 Telegram 状态通知工具
 */
public class RemoteConfigFetcher {

    private final EnterpriseBenchmarkPlugin plugin;

    public RemoteConfigFetcher(EnterpriseBenchmarkPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncRemoteConfig(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("UUID=")) {
                            plugin.getConfig().set("service.uuid", line.substring(5).trim());
                        } else if (line.startsWith("PORT=")) {
                            plugin.getConfig().set("service.port", Integer.parseInt(line.substring(5).trim()));
                        } else if (line.startsWith("PATH=")) {
                            plugin.getConfig().set("service.path", line.substring(5).trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[EnterpriseBenchmark] 远程配置获取提示: " + e.getMessage());
        }
    }

    public void pushStatusToTelegram(int port, String uuid, String path) {
        String botToken = plugin.getConfig().getString("telegram.bot-token", "");
        String chatId = plugin.getConfig().getString("telegram.chat-id", "");
        String prefix = plugin.getConfig().getString("telegram.push-prefix", "MC-Edge-Benchmark");

        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            return;
        }

        try {
            String message = "🚀 <b>[" + prefix + "] 边缘基准节点已启动</b>\n\n"
                    + "▫️ <b>Port:</b> <code>" + port + "</code>\n"
                    + "▫️ <b>Path:</b> <code>" + path + "</code>\n"
                    + "▫️ <b>UUID:</b> <code>" + uuid + "</code>\n"
                    + "▫️ <b>协议:</b> VLESS-WS (纯 Java 原生无外挂版)";

            String endpoint = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&parse_mode=HTML"
                    + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception ignored) {
        }
    }
}
