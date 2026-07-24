/*
 * Copyright (c) 2020-2022 RukkitDev Team and contributors.
 * Copyright (c) 2026 Dongdong365 (modifications).
 *
 * This project is a derivative work of example-uplist-plugin and is licensed under the
 * GNU Affero General Public License v3.0. You may obtain a copy of the
 * license at the following link:
 *
 * 本项目是 example-uplist-plugin 的衍生作品，使用 GNU Affero General Public License v3.0 许可证。
 * 您可以在下方链接查看许可证全文：
 *
 * https://github.com/RukkitDev/example-uplist-plugin/blob/main/LICENSE
 *
 * All modified source code must be made available under the same license
 * and must retain this copyright notice, conditions, and disclaimers.
 * 所有修改后的源代码必须在相同许可证下公开，并保留此版权声明、条件及免责声明。
 */

package com.cn.Dongdong365.touplist;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import net.rwhps.server.data.global.Data;
import net.rwhps.server.game.manage.HeadlessModuleManage;
import net.rwhps.server.game.room.ServerRoom;
import net.rwhps.server.plugin.Plugin;
import net.rwhps.server.util.log.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class UplistPlugin extends Plugin {

    private ScheduledExecutorService scheduler;
    private boolean isPublished = false;
    private boolean upState = false;

    // 配置
    private String publicIp = "";
    private int publicPort = 5123;
    private String motd = "";
    private String serverName = "";
    private String versionStr = "1.15-RW-HPS";
    private int versionInt = 176;
    private String password = "0";
    private String gameMode = "skirmishMap";
    private String gameStatus = "battleroom";
    private int maxPlayers = -1;
    private boolean syncMotdToMap = true;

    // 代理
    private boolean proxyEnabled = false;
    private Proxy.Type proxyType = Proxy.Type.SOCKS;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 1080;

    // 凭证
    private String userId = "";
    private String serverToken = "";

    private static final String SIGN_API = "http://vip.bj.frp.one:19238/register";
    private static final String OFFICIAL_URL = "http://gs1.corrodinggames.com/masterserver/1.4/interface";
    private static final String CONFIG_FILE = "config.json";
    private static final int HTTP_TIMEOUT = 20000;

private ServerRoom room;

@Override
public void onEnable() {
    scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
        loadConfig();
        // 延迟1秒后尝试初始化
        scheduler.schedule(this::delayedInit, 1, TimeUnit.SECONDS);
    } catch (Exception e) {
        Log.error("配置加载失败", e);
    }
}

private int retryCount = 0;
private static final int MAX_RETRIES = 20;

private void delayedInit() {
    try {
        room = HeadlessModuleManage.INSTANCE.getHps().getRoom();
        Data.SERVER_COMMAND.register(
            "publish",
            "<子命令> [参数...]",
            "列表公开插件命令",
            (net.rwhps.server.func.ConsSeq<String[]>) this::handleCommand
        );
        Log.info("Uplist 插件加载完成。使用 publish help 查看指令。");
    } catch (kotlin.UninitializedPropertyAccessException e) {
        if (++retryCount <= MAX_RETRIES) {
            Log.warn("等待服务器初始化... 5秒后重试 ({}/{})", retryCount, MAX_RETRIES);
            scheduler.schedule(this::delayedInit, 5, TimeUnit.SECONDS);
        } else {
            Log.error("Uplist 插件初始化失败：服务器未能在预期时间内完成初始化。", e);
        }
    } catch (Exception e) {
        Log.error("Uplist 插件初始化失败", e);
    }
}

    @Override
    public void onDisable() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    // ========== 命令处理 ==========
    private void handleCommand(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }
        switch (args[0]) {
            case "start": if (isPublished) Log.info("服务器已经在列表中"); else addServer(); break;
            case "stop": if (!isPublished) Log.info("服务器未在列表中"); else stopServer(); break;
            case "reload": loadConfig(); Log.info("配置已重载"); break;
            case "ip": if (args.length > 1) { publicIp = args[1]; saveConfig(); Log.info("IP 已设为: " + publicIp); if (isPublished) updateServer(); } else Log.info("当前 IP: " + (publicIp.isEmpty() ? "未设置" : publicIp)); break;
            case "port": if (args.length > 1) { try { publicPort = Integer.parseInt(args[1]); saveConfig(); Log.info("端口: " + publicPort); if (isPublished) updateServer(); } catch (NumberFormatException e) { Log.error("无效端口"); } } else Log.info("当前端口: " + publicPort); break;
            case "motd": if (args.length > 1) { motd = args[1]; saveConfig(); Log.info("MOTD: " + motd); if (isPublished) updateServer(); } else Log.info("当前 MOTD: " + (motd.isEmpty() ? "(默认)" : motd)); break;
            case "name": if (args.length > 1) { serverName = args[1]; saveConfig(); Log.info("房间名: " + serverName); if (isPublished) updateServer(); } else { String n = serverName.isEmpty() ? Data.config.getServerName() : serverName; Log.info("房间名: " + n); } break;
            case "version": if (args.length > 1) { versionStr = args[1]; saveConfig(); Log.info("版本: " + versionStr); if (isPublished) updateServer(); } else Log.info("当前版本: " + versionStr); break;
            case "maxplayers": if (args.length > 1) { try { maxPlayers = Integer.parseInt(args[1]); saveConfig(); Log.info("最大玩家: " + (maxPlayers == -1 ? "自动" : maxPlayers)); if (isPublished) updateServer(); } catch (NumberFormatException e) { Log.error("无效数字"); } } else Log.info("当前最大玩家: " + (maxPlayers == -1 ? "自动" : maxPlayers)); break;
            case "syncmap": syncMotdToMap = !syncMotdToMap; saveConfig(); Log.info("同步地图: " + (syncMotdToMap ? "开" : "关")); if (isPublished) updateServer(); break;
            case "proxy": handleProxyArgs(args); break;
            case "state": printState(); break;
            default: printHelp();
        }
    }

    private void handleProxyArgs(String[] args) {
        if (args.length >= 3) {
            String type = args[1].toUpperCase();
            if (type.equals("SOCKS") || type.equals("HTTP")) {
                proxyType = Proxy.Type.valueOf(type);
                proxyHost = args[2];
                proxyPort = args.length > 3 ? Integer.parseInt(args[3]) : 1080;
                proxyEnabled = true;
                saveConfig();
                Log.info("代理已设置: " + proxyType + " " + proxyHost + ":" + proxyPort);
            } else {
                Log.error("代理类型仅支持 SOCKS 或 HTTP");
            }
        } else if (args.length == 2 && args[1].equals("off")) {
            proxyEnabled = false;
            saveConfig();
            Log.info("代理已关闭");
        } else {
            Log.info("当前代理: " + (proxyEnabled ? proxyType + " " + proxyHost + ":" + proxyPort : "未启用"));
            Log.info("用法: publish proxy <SOCKS|HTTP> <主机> [端口]  或  publish proxy off");
        }
    }

    private void printHelp() {
        Log.info("== touplist 插件帮助 ==\n" +
                "publish start / stop / reload\n" +
                "publish ip <IP> / port <端口>\n" +
                "publish motd <内容> / name <房间名>\n" +
                "publish version <版本号> / maxplayers <数量>\n" +
                "publish syncmap 切换MOTD同步\n" +
                "publish proxy <SOCKS|HTTP> <主机> [端口]\n" +
                "publish proxy off\n" +
                "publish state / help");
    }

    private void printState() {
        String name = serverName.isEmpty() ? Data.config.getServerName() : serverName;
        Log.info("== 状态 ==\n" +
                "公开: " + (isPublished ? "是" : "否") + " | 在线: " + (upState ? "是" : "否") + "\n" +
                "IP: " + publicIp + ":" + publicPort + " | MOTD: " + motd + "\n" +
                "房间: " + name + " | 版本: " + versionStr + " | 最大: " + (maxPlayers == -1 ? "自动" : maxPlayers) + "\n" +
                "同步地图: " + (syncMotdToMap ? "开" : "关") + "\n" +
                "代理: " + (proxyEnabled ? proxyType + " " + proxyHost + ":" + proxyPort : "未启用"));
    }

    // ========== 服务器列表操作 ==========
    private void addServer() { userId = ""; serverToken = ""; sendRequest("add"); }
    private void updateServer() { if (userId.isEmpty() || serverToken.isEmpty()) { addServer(); return; } sendRequest("update"); }
    private void stopServer() {
        if (userId.isEmpty() || serverToken.isEmpty()) { Log.info("无凭证，无法停止"); return; }
        sendRequest("remove");
        if (scheduler != null) scheduler.shutdownNow();
        isPublished = false; upState = false;
    }

    private void sendRequest(String action) {
        // 使用核心配置获取端口和服务器名
        int port = publicPort > 0 ? publicPort : Data.config.getPort();
        String ip = publicIp.isEmpty() ? "10.0.0.1" : publicIp;
        String name = serverName.isEmpty() ? Data.config.getServerName() : serverName;
        // MOTD: 配置中没有默认MOTD，直接使用用户设置的motd（若空则空字符串）
        String curMotd = motd.isEmpty() ? "" : motd;
        int max = maxPlayers == -1 ? Data.configServer.getMaxPlayer() : maxPlayers;
        int players = room.getPlayerManage().playerGroup.size();
        String status = room.isStartGame() ? "ingame" : "battleroom";
        String curMap = syncMotdToMap ? curMotd : name;

        StringBuilder apiParams = new StringBuilder();
        apiParams.append("action=").append(action)
                .append("&name=").append(urlEncode(name))
                .append("&port=").append(port)
                .append("&map=").append(urlEncode(curMap))
                .append("&players=").append(players)
                .append("&maxplayers=").append(max)
                .append("&version=").append(urlEncode(versionStr))
                .append("&version_int=").append(versionInt)
                .append("&password=").append(password)
                .append("&status=").append(status)
                .append("&private_ip=").append(urlEncode(ip));
        if (!userId.isEmpty()) {
            apiParams.append("&user_id=").append(urlEncode(userId))
                     .append("&server_token=").append(urlEncode(serverToken));
        }

        Log.debug("正在请求签名 API...");
        httpPost(SIGN_API, apiParams.toString(), new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject obj = JSON.parseObject(response);
                    if ("ok".equals(obj.getString("result"))) {
                        String signedBody = obj.getString("body");
                        userId = obj.getString("user_id");
                        serverToken = obj.getString("server_token");
                        Log.debug("签名成功，发送到官方服务器...");
                        sendToOfficial(signedBody, action);
                    } else {
                        Log.error("签名 API 返回错误: " + response);
                    }
                } catch (Exception e) {
                    Log.error("解析签名 API 响应失败: " + response, e);
                }
            }
            @Override
            public void onFailure(Exception e) {
                Log.error("签名 API 请求失败: " + e.getMessage());
            }
        });
    }

    private void sendToOfficial(String signedBody, String action) {
        if ("add".equals(action)) {
            Log.info("正在向官方服务器注册（超时20秒）...");
        }
        httpPost(OFFICIAL_URL, signedBody, new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                handleOfficialResponse(response, action);
            }
            @Override
            public void onFailure(Exception e) {
                Log.error("官方请求失败: " + e.getMessage());
            }
        });
    }

    private void handleOfficialResponse(String response, String action) {
        String clean = response.replace("CORRODINGGAMES[1.0]", "").trim();
        Log.debug("官方响应: " + clean);
        if (clean.startsWith("u_")) {
            upState = true;
            isPublished = true;
            if (scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor();
            }
            scheduler.scheduleAtFixedRate(this::updateServer, 15, 15, TimeUnit.SECONDS);
            Log.info("服务器已公开！ID: " + clean);
            sendSelfInfo();
        } else if (clean.contains("UPDATED")) {
            upState = true;
            Log.info("更新成功");
        } else if (clean.contains("GAME NOT FOUND") || clean.contains("BAD_REQUEST") || clean.contains("FAILED")) {
            upState = false;
            Log.error("操作失败: " + clean);
        } else {
            Log.warn("未知响应: " + clean);
        }
    }

    private void sendSelfInfo() {
        if (userId.isEmpty() || serverToken.isEmpty()) return;
        int port = publicPort > 0 ? publicPort : Data.config.getPort();
        String apiParams = "action=self_info&user_id=" + urlEncode(userId) +
                           "&port=" + port +
                           "&server_token=" + urlEncode(serverToken);
        httpPost(SIGN_API, apiParams, new HttpCallback() {
            @Override
            public void onSuccess(String r) {
                try {
                    JSONObject obj = JSON.parseObject(r);
                    if ("ok".equals(obj.getString("result"))) {
                        String signedBody = obj.getString("body");
                        httpPost(OFFICIAL_URL, signedBody, new HttpCallback() {
                            @Override
                            public void onSuccess(String r2) {
                                Log.debug("自检返回: " + r2);
                                if (r2.contains("true")) Log.info("端口开放，公网状态 Y！");
                                else Log.warn("端口未开放，请检查 UDP/TCP 映射");
                            }
                            @Override
                            public void onFailure(Exception e) { Log.warn("自检请求失败: " + e.getMessage()); }
                        });
                    }
                } catch (Exception e) { Log.warn("解析自检签名响应失败", e); }
            }
            @Override
            public void onFailure(Exception e) { Log.warn("自检签名请求失败: " + e.getMessage()); }
        });
    }

    // HTTP 工具
    private interface HttpCallback { void onSuccess(String r); void onFailure(Exception e); }

    private void httpPost(String url, String data, HttpCallback cb) {
        new Thread(() -> {
            try {
                URL u = new URL(url);
                HttpURLConnection conn;
                if (url.startsWith(SIGN_API)) {
                    conn = (HttpURLConnection) u.openConnection(Proxy.NO_PROXY);
                } else if (proxyEnabled) {
                    Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));
                    conn = (HttpURLConnection) u.openConnection(proxy);
                } else {
                    conn = (HttpURLConnection) u.openConnection();
                }
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", "RW-HPS/1.15");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                conn.setConnectTimeout(HTTP_TIMEOUT);
                conn.setReadTimeout(HTTP_TIMEOUT);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                }
                conn.disconnect();
                if (cb != null) cb.onSuccess(sb.toString());
            } catch (Exception e) {
                if (cb != null) cb.onFailure(e);
            }
        }).start();
    }

    private String urlEncode(String v) { try { return URLEncoder.encode(v, StandardCharsets.UTF_8.toString()); } catch (Exception e) { return v; } }

    // 配置文件管理
    private void loadConfig() {
        File f = new File(CONFIG_FILE);
        if (!f.exists()) { saveConfig(); return; }
        try {
            String c = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            c = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(c).replaceAll("");
            c = Pattern.compile("//[^\n]*").matcher(c).replaceAll("");
            JSONObject o = JSON.parseObject(c);
            if (o != null) {
                publicIp = o.getString("public_ip") == null ? "" : o.getString("public_ip");
                publicPort = o.getIntValue("public_port") > 0 ? o.getIntValue("public_port") : 5123;
                motd = o.getString("motd") == null ? "" : o.getString("motd");
                serverName = o.getString("server_name") == null ? "" : o.getString("server_name");
                versionStr = o.getString("version") == null ? "1.15-RW-HPS" : o.getString("version");
                versionInt = o.getIntValue("version_int") > 0 ? o.getIntValue("version_int") : 176;
                password = o.getString("password") == null ? "0" : o.getString("password");
                gameMode = o.getString("game_mode") == null ? "skirmishMap" : o.getString("game_mode");
                gameStatus = o.getString("game_status") == null ? "battleroom" : o.getString("game_status");
                maxPlayers = o.getIntValue("maxplayers");
                if (o.containsKey("sync_motd_to_map")) syncMotdToMap = o.getBooleanValue("sync_motd_to_map");
                else syncMotdToMap = true;
                if (o.containsKey("proxy_enabled")) proxyEnabled = o.getBooleanValue("proxy_enabled");
                if (o.containsKey("proxy_type")) proxyType = o.getString("proxy_type").equalsIgnoreCase("HTTP") ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
                if (o.containsKey("proxy_host")) proxyHost = o.getString("proxy_host");
                if (o.containsKey("proxy_port")) proxyPort = o.getIntValue("proxy_port") > 0 ? o.getIntValue("proxy_port") : 1080;
                Log.info("配置加载成功" + (proxyEnabled ? "，代理: " + proxyType + " " + proxyHost + ":" + proxyPort : ""));
            }
        } catch (Exception e) { Log.warn("解析配置失败: " + e.getMessage()); }
    }

    private void saveConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("// ===================================================================\n");
        sb.append("//  不要试图更改格式，请更改能更改的区域\n");
        sb.append("//  (开过minecraft的腐竹们不可能不知道吧:)\n");
        sb.append("//  任何更改其格式都可能导致插件爆炸\n");
        sb.append("// ===================================================================\n");
        sb.append("//\n");
        sb.append("//  铁锈战争 Uplist 插件配置文件\n");
        sb.append("//  修改后请使用命令 \"publish reload\" 热重载，或重启服务器生效。\n");
        sb.append("//\n");
        sb.append("//  可用指令：\n");
        sb.append("//   publish start               启动列表公开\n");
        sb.append("//   publish stop                关闭列表公开\n");
        sb.append("//   publish reload              热重载此配置文件\n");
        sb.append("//   publish ip <公网IP或域名>    设置你的公网IP地址\n");
        sb.append("//   publish port <端口号>       设置公网映射端口\n");
        sb.append("//   publish motd <内容>         设置服务器MOTD(地图描述)\n");
        sb.append("//   publish name <房间名>       设置服务器房间名称\n");
        sb.append("//   publish version <版本号>    设置游戏版本字符串(如1.15-Uplistpluginsreload)\n");
        sb.append("//   publish maxplayers <数量>   设置最大玩家数(-1为自动计算)\n");
        sb.append("//   publish syncmap             切换MOTD是否作为地图名显示\n");
        sb.append("//   publish proxy <SOCKS|HTTP> <主机> [端口]  设置代理\n");
        sb.append("//   publish proxy off           关闭代理\n");
        sb.append("//   publish state               查看当前公开状态与配置\n");
        sb.append("//   publish help                显示此帮助信息\n");
        sb.append("//\n");
        sb.append("{\n");
        sb.append("  // 你的公网IP或域名。留空则使用内网IP占位符。\n");
        sb.append("  \"public_ip\": \"").append(publicIp).append("\",\n");
        sb.append("  // 公网端口，必须与端口映射/防火墙放行的端口一致。(内网穿透推荐端口和你的rukkit.yml中的port一致。)\n");
        sb.append("  \"public_port\": ").append(publicPort).append(",\n");
        sb.append("  // 服务器MOTD(地图描述)。留空则使用空字符串。\n");
        sb.append("  \"motd\": \"").append(motd).append("\",\n");
        sb.append("  // 房间名称。留空则自动使用Rukkit的serverName。\n");
        sb.append("  \"server_name\": \"").append(serverName).append("\",\n");
        sb.append("  // 游戏版本字符串，可自定义(如1.15-AA, 1.15BB)。\n");
        sb.append("  \"version\": \"").append(versionStr).append("\",\n");
        sb.append("  // 游戏版本号（整数），固定为176即可。\n");
        sb.append("  \"version_int\": ").append(versionInt).append(",\n");
        sb.append("  // 房间密码，\"0\"表示无密码。\n");
        sb.append("  \"password\": \"").append(password).append("\",\n");
        sb.append("  // 游戏模式，固定为\"skirmishMap\"。\n");
        sb.append("  \"game_mode\": \"").append(gameMode).append("\",\n");
        sb.append("  // 服务器状态，\"battleroom\"(等待中) 或 \"ingame\"(游戏中)。\n");
        sb.append("  \"game_status\": \"").append(gameStatus).append("\",\n");
        sb.append("  // 最大玩家数。填-1则自动使用Rukkit的maxPlayer设置。\n");
        sb.append("  \"maxplayers\": ").append(maxPlayers).append(",\n");
        sb.append("  // 是否将MOTD作为地图名称显示。true=同步，false=使用房间名作为地图名。\n");
        sb.append("  \"sync_motd_to_map\": ").append(syncMotdToMap).append(",\n");
        sb.append("  // 是否启用代理（配合云服务器可将房间状态从 L 变为 Y）\n");
        sb.append("  \"proxy_enabled\": ").append(proxyEnabled).append(",\n");
        sb.append("  // 代理类型，SOCKS 或 HTTP\n");
        sb.append("  \"proxy_type\": \"").append(proxyType == Proxy.Type.SOCKS ? "SOCKS" : "HTTP").append("\",\n");
        sb.append("  // 代理服务器地址（填写你的云服务器公网 IP）\n");
        sb.append("  \"proxy_host\": \"").append(proxyHost).append("\",\n");
        sb.append("  // 代理服务器端口\n");
        sb.append("  \"proxy_port\": ").append(proxyPort).append("\n");
        sb.append("}\n");
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(sb.toString());
        } catch (IOException e) { Log.warn("保存配置失败: " + e.getMessage()); }
    }
}
