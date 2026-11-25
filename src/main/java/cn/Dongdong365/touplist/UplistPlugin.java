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
 * https://github.com/Dongdong365/touplist/blob/master/LICENSE
 *
 * All modified source code must be made available under the same license
 * and must retain this copyright notice, conditions, and disclaimers.
 * 所有修改后的源代码必须在相同许可证下公开，并保留此版权声明、条件及免责声明。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * 本程序是自由软件：您可以根据自由软件基金会发布的 GNU Affero 通用公共许可证
 * 第 3 版（或您选择的任何更高版本）的条款重新分发和/或修改它。
 *
 * 本程序分发的目的是希望它有用，但没有任何担保；甚至没有适销性或
 * 特定用途适用性的暗示担保。详情请参见 GNU Affero 通用公共许可证。
 */

package com.cn.Dongdong365.touplist;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import net.rwhps.server.data.global.Data;
import net.rwhps.server.game.GameMaps;
import net.rwhps.server.game.manage.HeadlessModuleManage;
import net.rwhps.server.game.room.ServerRoom;
import net.rwhps.server.plugin.Plugin;
import net.rwhps.server.util.game.command.CommandHandler;
import net.rwhps.server.util.log.Log;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UplistPlugin extends Plugin {

    private ScheduledExecutorService scheduler;
    /** 续期任务是否正在运行（与 isPublished/upState 独立，避免受 onEnable 预创建 scheduler 影响） */
    private boolean renewing = false;
    private boolean isPublished = false;
    private boolean upState = false;
    /** 版本检查开关（来自配置 update-check） */
    private boolean updateCheckEnabled = true;
    private final RemoteVersionChecker versionChecker = new RemoteVersionChecker();

    // 互斥锁：同一时刻只允许一个上架/下架/更新任务（覆盖签名+两个官方请求全程）
    private final java.util.concurrent.atomic.AtomicBoolean opBusy = new java.util.concurrent.atomic.AtomicBoolean(false);

    // 配置（默认值与 src/main/resources/config.json 保持一致）
    private String publicIp = "example.com";
    private int publicPort = 16675;
    private String motd = "";
    private String serverName = "";
    private String versionStr = "1.15-ToUplist";
    private int versionInt = 176;
    private String password = "0";
    private String gameMode = "skirmishMap";
    private volatile String gameStatus = "battleroom";
    private volatile boolean syncGameStatus = true;
    private int maxPlayers = 10;
    private boolean syncMotdToMap = true;

    // 代理
    private boolean proxyEnabled = false;
    private Proxy.Type proxyType = Proxy.Type.HTTP;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 1080;
    private String proxyUser = "";
    private String proxyPass = "";

    // 凭证（两个服务器共用同一套）
    private String userId = "";
    private String serverToken = "";
    private boolean serverUp1 = false, serverUp2 = false;

    private static final String SIGN_API = "http://160.202.46.58:19238/register";
    private static final String STATUS_API = "http://160.202.46.58:19238/status";
    private static final String OFFICIAL_URL_1 = "http://gs1.corrodinggames.com/masterserver/1.4/interface";
    private static final String OFFICIAL_URL_2 = "http://gs4.corrodinggames.net/masterserver/1.4/interface";
    private static final String CONFIG_FILE = "config.yml";
    private static final int HTTP_TIMEOUT = 20000;

/**
     * 注册服务器命令（框架在加载插件时调用，handler 即 Data.SERVER_COMMAND）。
     * 命令注册与 room 初始化解耦，执行时才按需取 room。
     */
    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register(
            "publish",
            "<子命令> [参数...]",
            "列表公开插件命令",
            (net.rwhps.server.func.ConsSeq<String[]>) this::handleCommand
        );
    }

    public void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        new RemoteVersionChecker().checkAndLog();
        try {
            loadConfig();
            versionChecker.setEnabled(updateCheckEnabled);
            if (updateCheckEnabled) {
                versionChecker.init(scheduler);
            } else {
                versionChecker.logDisabled();
            }
            Log.info("Uplist 插件加载完成。使用 publish help 查看指令。");
            fetchDiagnostic();
        } catch (Exception e) {
            Log.error("配置加载失败，尝试解决或备份配置文件后删除以重新生成", e);
        }
    }

    @Override
    public void onDisable() {
        versionChecker.cancelPeriodicTasks();
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
            case "motd": if (args.length > 1) { motd = args[1]; saveConfig(); Log.info("MOTD: " + motd); if (isPublished) updateServer(); } else Log.info("当前 MOTD: " + (motd.isEmpty() ? "(空)" : motd)); break;
            case "name": if (args.length > 1) { serverName = args[1]; saveConfig(); Log.info("房间名: " + serverName); if (isPublished) updateServer(); } else { String n = serverName.isEmpty() ? Data.config.getServerName() : serverName; Log.info("房间名: " + n); } break;
            case "version": if (args.length > 1) { versionStr = args[1]; saveConfig(); Log.info("版本: " + versionStr); if (isPublished) updateServer(); } else Log.info("当前版本: " + versionStr); break;
            case "maxplayers": if (args.length > 1) { try { maxPlayers = Integer.parseInt(args[1]); saveConfig(); Log.info("最大玩家: " + (maxPlayers == -1 ? "自动" : maxPlayers)); if (isPublished) updateServer(); } catch (NumberFormatException e) { Log.error("无效数字"); } } else Log.info("当前最大玩家: " + (maxPlayers == -1 ? "自动" : maxPlayers)); break;
            case "syncmap": syncMotdToMap = !syncMotdToMap; saveConfig(); Log.info("同步地图: " + (syncMotdToMap ? "开（实际地图）" : "关（自定义MOTD）")); if (isPublished) updateServer(); break;
            case "syncstatus": syncGameStatus = !syncGameStatus; saveConfig(); Log.info("状态同步: " + (syncGameStatus ? "开（实际房间状态）" : "关（手动编辑game_status）")); if (isPublished) updateServer(); break;
            case "status":
                if (args.length > 1) {
                    try {
                        gameStatus = normalizeGameStatus(args[1]);
                        saveConfig();
                        Log.info("手动房间状态: " + gameStatus + (syncGameStatus ? "（状态同步开启，关闭后生效）" : ""));
                        if (isPublished && !syncGameStatus) updateServer();
                    } catch (IllegalArgumentException e) {
                        Log.error(e.getMessage());
                    }
                } else {
                    Log.info("手动房间状态: " + gameStatus + " | 状态同步: " + (syncGameStatus ? "开" : "关"));
                }
                break;
            case "proxy": handleProxyArgs(args); break;
            case "state": printState(); break;
            case "update": if (versionChecker.isChecking()) Log.info("正在检查中..."); else versionChecker.runCheckNow(); break;
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
                // 可选：publish proxy <类型> <主机> [端口] [用户] [密码]
                if (args.length > 4) {
                    proxyUser = args[4];
                    proxyPass = args.length > 5 ? args[5] : "";
                } else {
                    proxyUser = "";
                    proxyPass = "";
                }
                saveConfig();
                Log.info("代理已设置: " + proxyType + " " + proxyHost + ":" + proxyPort
                        + (proxyUser.isEmpty() ? "" : " user=" + proxyUser));
            } else {
                Log.error("代理类型仅支持 SOCKS 或 HTTP");
            }
        } else if (args.length == 2 && args[1].equals("off")) {
            proxyEnabled = false;
            proxyUser = "";
            proxyPass = "";
            saveConfig();
            Log.info("代理已关闭");
        } else {
            Log.info("当前代理: " + (proxyEnabled ? proxyType + " " + proxyHost + ":" + proxyPort : "未启用"));
            Log.info("用法: publish proxy <SOCKS|HTTP> <主机> [端口] [用户] [密码]   或   publish proxy off");
        }
    }

    private void printHelp() {
        Log.info("== touplist 插件帮助 ==\n" +
                "publish start / stop / reload\n" +
                "publish ip <IP> / port <端口>\n" +
                "publish motd <内容> / name <房间名>\n" +
                "publish version <版本号> / maxplayers <数量>\n" +
                "publish syncmap 切换地图同步（开=实际地图，关=自定义MOTD）\n" +
                "publish syncstatus 切换状态同步（开=实际状态，关=手动game_status）\n" +
                "publish status <battleroom|ingame> 设置手动列表状态\n" +
                "publish proxy <SOCKS|HTTP> <主机> [端口] [用户] [密码]\n" +
                "publish proxy off\n" +
                "publish update 手动检查更新\n" +
                "publish state / help");
    }

    private void printState() {
        String name = serverName.isEmpty() ? Data.config.getServerName() : serverName;
        String actualStatus = "服务器暂未开启，稍等。（初始化）";
        try {
            actualStatus = selectListGameStatus(true, HeadlessModuleManage.INSTANCE.getHps().getRoom().isStartGame(), gameStatus);
        } catch (Exception ignored) { }
        Log.info("== 状态 ==\n" +
                "公开: " + (isPublished ? "是" : "否") + " | 在线: " + (upState ? "是" : "否") + "\n" +
                "gs1 在线: " + (serverUp1 ? "是" : "否") + " | gs4 在线: " + (serverUp2 ? "是" : "否") + "\n" +
                "IP: " + publicIp + ":" + publicPort + " | MOTD: " + motd + "\n" +
                "房间: " + name + " | 版本: " + versionStr + " | 最大: " + (maxPlayers == -1 ? "自动" : maxPlayers) + "\n" +
                "同步地图: " + (syncMotdToMap ? "开（实际地图）" : "关（自定义MOTD）") + "\n" +
                "列表游戏状态: " + (syncGameStatus ? actualStatus : gameStatus) + " | 状态同步: " + (syncGameStatus ? "开" : "关") + "\n" +
                "实际房间状态: " + actualStatus + " | 手动game_status: " + gameStatus + "\n" +
                "代理: " + (proxyEnabled ? proxyType + " " + proxyHost + ":" + proxyPort + (proxyUser.isEmpty() ? "" : " user=" + proxyUser) : "未启用"));
    }

    // ========== 服务器列表操作 ==========
    private void addServer() { userId = ""; sendRequest("add"); }
    private void updateServer() { if (userId.isEmpty()) { addServer(); return; } sendRequest("update"); }
    private void stopServer() {
        sendRequest("remove");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = null;
        renewing = false; isPublished = false; upState = false;
        userId = ""; serverToken = "";
        serverUp1 = false; serverUp2 = false;
    }

    static String selectListMapName(boolean syncMap, GameMaps maps, String customMotd) {
        return syncMap ? maps.getMapName() : customMotd;
    }

    static String selectListGameStatus(boolean syncStatus, boolean isStartGame, String customStatus) {
        return syncStatus ? (isStartGame ? "ingame" : "battleroom") : customStatus;
    }

    static String normalizeGameStatus(Object value) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            return "battleroom";
        }
        if (value instanceof String) {
            String status = ((String) value).trim().toLowerCase(Locale.ROOT);
            if (status.equals("battleroom") || status.equals("ingame")) return status;
        }
        throw new IllegalArgumentException("game_status 只允许 battleroom 或 ingame");
    }

    private void sendRequest(String action) {
        // 惰性获取 room：服务器未初始化完成时提示并返回
        ServerRoom r;
        try {
            r = HeadlessModuleManage.INSTANCE.getHps().getRoom();
        } catch (Exception e) {
            Log.warn("服务器尚未初始化完成，请稍后再试");
            return;
        }
        // 互斥锁：同一时刻只允许一个上架/下架/更新任务，防止重复注册
        if (!opBusy.compareAndSet(false, true)) {
            Log.warn("当前服务器已有上架/下架/更新任务在进行中，请手动结束稍后再试或重启服务端");
            return;
        }
        // 使用核心配置获取端口和服务器名
        int port = publicPort > 0 ? publicPort : Data.config.getPort();
        String ip = publicIp.isEmpty() ? "10.0.0.1" : publicIp;
        String name = serverName.isEmpty() ? Data.config.getServerName() : serverName;
        int max = maxPlayers == -1 ? Data.configServer.getMaxPlayer() : maxPlayers;
        int players = r.getPlayerManage().playerGroup.size();
        String status = selectListGameStatus(syncGameStatus, r.isStartGame(), gameStatus);
        // true 同步当前实际地图；false 使用插件自定义 MOTD。每次上报实时读取，不截断。
        String curMap = selectListMapName(syncMotdToMap, r.getMaps(), motd);

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
                        Log.debug("签名成功，同时发送到两个官方服务器...");
                        // 锁在 sendToBothServers 两个官方请求全部回调后释放
                        sendToBothServers(signedBody, action, userId, serverToken);
                    } else {
                        Log.error("签名 API 返回错误: " + response);
                        opBusy.set(false);
                    }
                } catch (Exception e) {
                    Log.error("解析签名 API 响应失败: " + response, e);
                    opBusy.set(false);
                }
            }
            @Override
            public void onFailure(Exception e) {
                Log.error("签名 API 请求失败: " + e.getMessage());
                opBusy.set(false);
            }
        });
    }

    private void sendToBothServers(String signedBody, String action, String uid, String token) {
        if ("add".equals(action)) {
            Log.info("正在向官方服务器注册（超时20秒）...");
        }
        // 两个官方请求都回调（成功或失败）后释放互斥锁
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(2);
        Runnable onEach = () -> { if (remaining.decrementAndGet() <= 0) opBusy.set(false); };
        httpPost(OFFICIAL_URL_1, signedBody, wrapDone(buildCallback(1, action), onEach));
        httpPost(OFFICIAL_URL_2, signedBody, wrapDone(buildCallback(2, action), onEach));
    }

    private HttpCallback wrapDone(HttpCallback inner, Runnable onDone) {
        return new HttpCallback() {
            @Override
            public void onSuccess(String r) { try { inner.onSuccess(r); } finally { onDone.run(); } }
            @Override
            public void onFailure(Exception e) { try { inner.onFailure(e); } finally { onDone.run(); } }
        };
    }

    private HttpCallback buildCallback(int serverNum, String action) {
        String urlLabel = serverNum == 1 ? "gs1" : "gs4";
        return new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                handleOfficialResponse(response, action, serverNum, urlLabel);
            }
            @Override
            public void onFailure(Exception e) {
                Log.error(urlLabel + " 服务器请求失败: " + e.getMessage());
            }
        };
    }

    private void handleOfficialResponse(String response, String action, int serverNum, String urlLabel) {
        String clean = response.replace("CORRODINGGAMES[1.0]", "").trim();
        Log.debug(urlLabel + " 响应: " + clean);

        if (clean.startsWith("u_")) {
            if (serverNum == 1) serverUp1 = true; else serverUp2 = true;
            Log.info(urlLabel + " 服务器注册/更新成功！ID: " + clean);
        } else if (clean.contains("UPDATED")) {
            if (serverNum == 1) serverUp1 = true; else serverUp2 = true;
            Log.info(urlLabel + " 更新成功");
        } else if (clean.contains("GAME NOT FOUND") || clean.contains("BAD_REQUEST") || clean.contains("FAILED")) {
            if (serverNum == 1) serverUp1 = false; else serverUp2 = false;
            Log.error(urlLabel + " 操作失败: " + clean);
        } else {
            Log.warn(urlLabel + " 未知响应: " + clean);
        }

        // 更新聚合状态
        isPublished = serverUp1 || serverUp2;
        upState = serverUp1 || serverUp2;

        // 任一服务器在线则启动定时上报
        if (upState && !renewing) {
            renewing = true;
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::updateServer, 15, 15, TimeUnit.SECONDS);
            Log.info("服务器已公开！每15秒自动续期。");
            sendSelfInfo();
        } else if (!upState && renewing) {
            renewing = false;
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    private void sendSelfInfo() {
        // 分别对每个服务器发送自检
        if (!userId.isEmpty()) {
            sendSelfInfoTo(userId, serverToken, OFFICIAL_URL_1, "gs1");
            sendSelfInfoTo(userId, serverToken, OFFICIAL_URL_2, "gs4");
        }
    }

    private void sendSelfInfoTo(String uid, String token, String url, String label) {
        String apiParams = "action=self_info&user_id=" + urlEncode(uid) +
                           "&port=" + publicPort +
                           "&server_token=" + urlEncode(token);
        httpPost(SIGN_API, apiParams, new HttpCallback() {
            @Override
            public void onSuccess(String r) {
                try {
                    JSONObject obj = JSON.parseObject(r);
                    if ("ok".equals(obj.getString("result"))) {
                        String signedBody = obj.getString("body");
                        httpPost(url, signedBody, new HttpCallback() {
                            @Override
                            public void onSuccess(String r2) {
                                Log.debug(label + " 自检返回: " + r2);
                                if (r2.contains("true")) Log.info(label + " 端口开放，公网状态 Y！");
                                else Log.error(label + " 端口未开放，你没有开放到列表。内网穿透玩家请检查 UDP/TCP 映射配置是否正确，代理是否开启。提交的ip是否正确，公网ip的防火墙是否打开。");
                            }
                            @Override
                            public void onFailure(Exception e) { Log.error(label + " 自检请求失败: " + e.getMessage()); }
                        });
                    }
                } catch (Exception e) { Log.error(label + " 解析自检签名响应失败：", e); }
            }
            @Override
            public void onFailure(Exception e) { Log.error(label + " 自检签名请求失败: " + e.getMessage()); }
        });
    }

    private interface HttpCallback { void onSuccess(String r); void onFailure(Exception e); }

    private void fetchDiagnostic() {
        httpGet(STATUS_API, new HttpCallback() {
            @Override
            public void onSuccess(String r) { Log.info("[诊断] 签名服务器在线: " + r); }
            @Override
            public void onFailure(Exception e) { Log.error("[诊断] 连接签名服务器 失败，无法确认签名API是否在线。请访问签名API的监控系统(在仓库内Dongdong365/ToUpList-API-Whitelist)查看在线状态。失败原因： " + e.getMessage()); }
        });
    }

    private void httpGet(String url, HttpCallback cb) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(HTTP_TIMEOUT);
                conn.setReadTimeout(HTTP_TIMEOUT);
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                conn.disconnect();
                if (cb != null) cb.onSuccess(sb.toString());
            } catch (Exception e) {
                if (cb != null) cb.onFailure(new IOException(enrichError(conn, e)));
            }
        }).start();
    }

    /** 请求失败时把服务端返回的错误体（如 400 的响应原因）拼进异常信息，便于排查。 */
    private String enrichError(HttpURLConnection conn, Exception e) {
        String original = e.getMessage();
        try {
            if (conn != null) {
                int code = conn.getResponseCode();
                if (code >= 400) {
                    StringBuilder eb = new StringBuilder();
                    try (java.io.InputStream es = conn.getErrorStream()) {
                        if (es != null) {
                            java.io.BufferedReader er = new java.io.BufferedReader(new java.io.InputStreamReader(es, StandardCharsets.UTF_8));
                            String l;
                            while ((l = er.readLine()) != null) eb.append(l);
                        }
                    }
                    if (eb.length() > 0) {
                        return "HTTP " + code + " 响应: " + eb + "（原始: " + original + "）";
                    }
                }
            }
        } catch (Exception ignore) { }
        return original;
    }

    private void httpPost(String url, String data, HttpCallback cb) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
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
                // 仅 HTTP 代理支持 Basic 认证（SOCKS v4a/v5 走独立鉴权通道，无需此头部）
                if (proxyEnabled && proxyType == Proxy.Type.HTTP && !proxyUser.isEmpty()) {
                    String encoded = java.util.Base64.getEncoder().encodeToString(
                            (proxyUser + ":" + proxyPass).getBytes(StandardCharsets.UTF_8));
                    conn.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
                }
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
                if (cb != null) cb.onFailure(new IOException(enrichError(conn, e)));
            }
        }).start();
    }

    private String urlEncode(String v) { try { return URLEncoder.encode(v, StandardCharsets.UTF_8.toString()); } catch (Exception e) { return v; } }

    // 配置文件管理（YAML 格式，支持标准 # 行注释，SnakeYAML 原生解析）
    private void loadConfig() {
        File f = new File(CONFIG_FILE);
        if (!f.exists()) {
            // 首次运行：从 classpath 的 resources/config.yml 模板原样释放到工作目录
            if (releaseDefaultConfig(f)) {
                Log.info("已生成默认配置文件 " + CONFIG_FILE + "（来自内置模板）");
            } else {
                Log.warn("未找到内置模板，使用代码默认值生成 " + CONFIG_FILE);
                saveConfig();
            }
        }
        try {
            org.yaml.snakeyaml.DumperOptions opts = new org.yaml.snakeyaml.DumperOptions();
            opts.setIndent(2);
            opts.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(opts);
            Map<String, Object> m;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                m = yaml.load(reader);
            }
            if (m == null) m = new HashMap<>();
            publicIp       = firstStr(m, "public_ip",        "example.com");
            publicPort     = firstInt(m, "public_port",      16675, 1);
            motd           = firstStr(m, "motd",             "");
            serverName     = firstStr(m, "server_name",      "");
            versionStr     = firstStr(m, "version",          "1.15-ToUplist");
            versionInt     = firstInt(m, "version_int",      176,   1);
            password       = firstStr(m, "password",         "0");
            gameMode       = firstStr(m, "game_mode",        "skirmishMap");
            try {
                gameStatus = normalizeGameStatus(m.get("game_status"));
            } catch (IllegalArgumentException e) {
                gameStatus = "battleroom";
                Log.warn(e.getMessage() + "，本次使用默认值 battleroom，请检查 " + CONFIG_FILE);
            }
            syncGameStatus = firstBool(m, "sync_game_status", true);
            maxPlayers     = firstInt(m, "maxplayers",       10,    0);
            syncMotdToMap  = firstBool(m, "sync_motd_to_map", true);
            proxyEnabled   = firstBool(m, "proxy_enabled",   false);
            proxyType      = "SOCKS".equalsIgnoreCase(firstStr(m, "proxy_type", "HTTP"))
                             ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            proxyHost      = firstStr(m, "proxy_host", "127.0.0.1");
            proxyPort      = firstInt(m, "proxy_port", 1080, 1);
            proxyUser      = firstStr(m, "proxy_user", "");
            proxyPass      = firstStr(m, "proxy_pass", "");
            updateCheckEnabled = firstBool(m, "update-check", true);
            try {
                ensureStatusConfigKeys(f, m);
                ensureUpdateCheckKeys(f, m);
            } catch (IOException e) {
                Log.warn("补齐状态配置失败，运行时已使用默认值: " + e.getMessage());
            }
            Log.info("配置加载成功" + (proxyEnabled ? "，代理: " + proxyType + " " + proxyHost + ":" + proxyPort + (proxyUser.isEmpty() ? "" : " user=" + proxyUser) : ""));
        } catch (Exception e) { Log.warn("解析配置失败: " + e.getMessage()); }
    }
    /** 只追加旧配置缺失的状态键，不覆盖服主的手动值、同步开关和注释。 */
    static void ensureStatusConfigKeys(File file, Map<?, ?> config) throws IOException {
        StringBuilder missing = new StringBuilder();
        if (!config.containsKey("sync_game_status")) {
            missing.append("\n# 状态同步：true=实际房间状态，false=使用手动game_status。\n")
                   .append("sync_game_status: true\n");
        }
        if (!config.containsKey("game_status")) {
            missing.append("\n# 手动列表状态（battleroom或ingame），仅关闭状态同步时生效。\n")
                   .append("game_status: battleroom\n");
        }
        if (missing.length() > 0) {
            Files.writeString(file.toPath(), missing.toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        }
    }
    /** 只追加旧配置缺失的更新检查键。 */
    static void ensureUpdateCheckKeys(File file, Map<?, ?> config) throws IOException {
        StringBuilder missing = new StringBuilder();
        if (!config.containsKey("update-check")) {
            missing.append("\n# ── 版本更新检查 ───────────────────────────────────────────────\n")
                   .append("# 是否开启后台定时检查更新（每次随机 60～240 分钟再查一次）\n")
                   .append("# true=开启，false=关闭。可通过 publish update 手动触发检查。\n")
                   .append("update-check: true\n");
        }
        if (missing.length() > 0) {
            Files.writeString(file.toPath(), missing.toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private static String firstStr(Map<?,?> m, String k, String def) {
        Object v = m.get(k); return (v == null || ((String)v).isEmpty()) ? def : (String)v;
    }
    private static int firstInt(Map<?,?> m, String k, int def, int min) {
        Object v = m.get(k);
        if (v instanceof Number n) { int i = n.intValue(); return i > min ? i : def; }
        try { int i = Integer.parseInt(((String)v).trim()); return i > min ? i : def; }
        catch (Exception ignored) {} return def;
    }
    private static boolean firstBool(Map<?,?> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (!(v instanceof String s)) return def;
        return s.trim().equalsIgnoreCase("true") || s.trim().equalsIgnoreCase("yes") || s.trim().equalsIgnoreCase("on");
    }

    /**
     * 把内置 resources/config.yml 模板释放到工作目录，返回是否成功。
     * 若资源不存在（例如打包时漏掉），返回 false 由调用方走代码默认值兜底。
     */
    private static boolean releaseDefaultConfig(File target) {
        try (InputStream in = UplistPlugin.class.getResourceAsStream("/config.yml")) {
            if (in == null) return false;
            Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.warn("释放默认配置失败: " + e.getMessage());
            return false;
        }
    }

    private void saveConfig() {
        try {
            // 写盘前先静默重载，确保写入的是最新磁盘值而非旧内存缓存
            loadConfig();
            File f = new File(CONFIG_FILE);
            List<String> lines = f.exists() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
            Set<String> touchedKeys = new HashSet<>();
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    out.add(line);
                    continue;
                }
                int colon = trimmed.indexOf(':');
                if (colon < 0) { out.add(line); continue; }
                String key = trimmed.substring(0, colon).trim();
                if (!MODIFIED_FIELDS.contains(key)) {
                    out.add(line);
                    continue;
                }
                touchedKeys.add(key);
                // 保留原始行的前导空白（若有），以 "key: value" 格式重写值
                int prefix = 0;
                while (prefix < line.length() && Character.isWhitespace(line.charAt(prefix))) prefix++;
                String formatted = formatYamlValue(getModifiedValue(key));
                out.add(line.substring(0, prefix) + key + ": " + formatted);
            }
            for (String k : MODIFIED_FIELDS) {
                if (!touchedKeys.contains(k)) {
                    out.add("");
                    out.add(k + ": " + formatYamlValue(getModifiedValue(k)));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String l : out) sb.append(l).append('\n');
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { Log.warn("保存配置失败: " + e.getMessage()); }
    }
    /** 命令级修改过的字段集合 —— 只有这些字段才会被子写入 */
    private static final Set<String> MODIFIED_FIELDS = new HashSet<>(java.util.Arrays.asList(
            "public_ip", "public_port", "server_name", "motd", "version",
            "password", "game_mode", "game_status", "sync_game_status", "maxplayers",
            "sync_motd_to_map", "proxy_enabled", "proxy_type",
            "proxy_host", "proxy_port", "proxy_user", "proxy_pass",
            "update-check"
    ));
    private Object getModifiedValue(String key) {
        switch (key) {
            case "public_ip":       return publicIp;
            case "public_port":     return publicPort;
            case "server_name":     return serverName;
            case "motd":            return motd;
            case "version":         return versionStr;
            case "password":        return password;
            case "game_mode":       return gameMode;
            case "game_status":     return gameStatus;
            case "sync_game_status":return syncGameStatus;
            case "maxplayers":      return maxPlayers;
            case "sync_motd_to_map":return syncMotdToMap;
            case "proxy_enabled":   return proxyEnabled;
            case "proxy_type":      return proxyType == Proxy.Type.SOCKS ? "SOCKS" : "HTTP";
            case "proxy_host":      return proxyHost;
            case "proxy_port":      return proxyPort;
            case "proxy_user":      return proxyUser;
            case "proxy_pass":      return proxyPass;
            case "update-check":    return updateCheckEnabled;
            default: return "";
        }
    }


    /** 把 Java 值渲染成合法的 YAML 标量（不带多余引号）。 */
    private static String formatYamlValue(Object v) {
        if (v == null) return "~";
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Number n) return n.toString();
        String s = v.toString();
        if (s.isEmpty()) return "''";
        // 仅当包含会改变语义的特殊字符时才加双引号
        if (!s.matches("[^\\s#:\\-?,\\[\\]{}&*!|@'%\"\\\\]+")) {
            s = s.replace("\\", "\\\\").replace("\"", "\\\"")
                 .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
            return "\"" + s + "\"";
        }
        return s;
    }
}
