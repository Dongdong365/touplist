package com.cn.Dongdong365.touplist;

import net.rwhps.server.util.log.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class RemoteVersionChecker {
    // ======================== 可配置常量（只改这里） ========================
    private static final String PLUGIN_NAME = "toulist-plugin";
    private static final String CURRENT_VERSION = "v26.11.25";
    private static final String REMOTE_URL = "https://www.rustedsvrwiki.de5.net/dongserverpluginslastversion.txt";
    private static final String PREFIX = "toulist";
    // =====================================================================

    // ======================== 运行时配置 ========================
    private boolean enabled = true;
    private long intervalMs = 240 * 60_000L;
    // =========================================================================

    // ======================== 硬编码消息 ========================
    private static final List<String> MESSAGES_LATEST = Arrays.asList(
        "插件是最新版本！及时更新插件是最好的习惯！请继续保持哦~"
    );
    private static final List<String> MESSAGES_UPDATE = Arrays.asList(
        "=============================================",
        "检测到新版本 {version}，当前版本已过时，建议尽快更新！",
        "重要：非最新版本产生的问题，谢绝向官方仓库反馈。",
        "更新前请阅读仓库 README 与更新日志，了解改动内容",
        "更新链接：https://github.com/Dongdong365/toulist/releases/latest",
        "提示：本插件依赖第三方API，API地址存在变更、失效风险。长期不更新可能导致列表功能无法正常使用。",
        "============================================="
    );
    private static final List<String> MESSAGES_DISABLED = Arrays.asList(
        "更新检测已关闭",
        "如需开启，请将 config.yml 内 update-check 设置为 true 后重启服务端。",
        "不建议长期关闭更新检测，可能错过重要修复与兼容性变更。"
    );
    // =====================================================================

    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(
        (Runnable r) -> {
            Thread t = new Thread(r, "toulist(publish)");
            t.setDaemon(true);
            return t;
        }
    );

    private final String pluginName;
    private final String currentVersion;
    private final String remoteUrl;
    private final String prefix;
    private volatile boolean checked = false;
    private volatile ScheduledExecutorService periodicScheduler;

    public RemoteVersionChecker() {
        this(PLUGIN_NAME, CURRENT_VERSION, REMOTE_URL, PREFIX);
    }

    public RemoteVersionChecker(String pluginName, String currentVersion, String remoteUrl, String prefix) {
        this.pluginName = pluginName;
        this.currentVersion = currentVersion;
        this.remoteUrl = remoteUrl;
        this.prefix = prefix;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void init(ScheduledExecutorService externalScheduler) {
        if (!enabled) return;
        cancelPeriodicTasks();
        scheduleNext(externalScheduler);
    }

    public void cancelPeriodicTasks() {
        if (periodicScheduler != null && !periodicScheduler.isShutdown()) {
            periodicScheduler.shutdownNow();
            periodicScheduler = null;
        }
    }

    private void scheduleNext(ScheduledExecutorService externalScheduler) {
        if (!enabled) return;
        long delayMs = intervalMs / 3L + (long)(Math.random() * (2L * intervalMs / 3L));
        periodicScheduler = Executors.newSingleThreadScheduledExecutor(
            (Runnable r) -> {
                Thread t = new Thread(r, "toulist(update-check)");
                t.setDaemon(true);
                return t;
            }
        );
        periodicScheduler.schedule(() -> {
            checked = false;
            checkAndLog();
            scheduleNext(externalScheduler);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void runCheckNow() {
        checked = false;
        checkAndLog();
    }

    /** 配置关闭时打印提示消息。 */
    public void logDisabled() {
        for (String msg : MESSAGES_DISABLED) Log.warn("[" + prefix + "] " + msg);
    }

    public void checkAndLog() {
        if (checked) return;
        checked = true;
        CompletableFuture.runAsync(() -> {
            try {
                String latest = fetchRemoteVersion();
                boolean hasNew = latest != null && isNewerVersion(latest, currentVersion);
                List<String> messages = hasNew ? MESSAGES_UPDATE : MESSAGES_LATEST;
                for (String raw : messages) {
                    String out = raw.replace("{prefix}", prefix)
                                    .replace("{version}", hasNew ? latest : currentVersion);
                    if (hasNew) Log.warn(out);
                    else Log.info(out);
                }
            } catch (Exception e) {
                Log.error("[" + prefix + "] 检查更新失败，请手动检查更新。当然，该插件仍能继续运行(正常使用无法保证)。", e);
            }
        }, UPDATE_EXECUTOR);
    }

    public String checkForUpdatesSync() throws IOException {
        String latest = fetchRemoteVersion();
        if (latest != null && isNewerVersion(latest, currentVersion)) return latest;
        return null;
    }

    public void resetCheck() { checked = false; }

    private String fetchRemoteVersion() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Java-UpdateChecker");
        try {
            if (conn.getResponseCode() != 200)
                throw new IOException("HTTP " + conn.getResponseCode());
            StringBuilder content = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) content.append(line).append("\n");
            }
            String pattern = "\"" + pluginName + "\"\\s*=\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content.toString());
            if (m.find()) return m.group(1);
            pattern = pluginName + "\\s*=\\s*([^,\\s]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(content.toString());
            if (m.find()) return m.group(1);
            throw new IOException("该插件未被RustedSvrWiki注册: " + pluginName);
        } finally {
            conn.disconnect();
        }
    }

    private boolean isNewerVersion(String latest, String current) {
        String l = latest.replaceAll("^[vV]", "");
        String c = current.replaceAll("^[vV]", "");
        String[] lp = l.split("\\."), cp = c.split("\\.");
        int max = Math.max(lp.length, cp.length);
        for (int i = 0; i < max; i++) {
            int lv = i < lp.length ? parseIntSafe(lp[i]) : 0;
            int cv = i < cp.length ? parseIntSafe(cp[i]) : 0;
            if (lv != cv) return lv > cv;
        }
        return false;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
