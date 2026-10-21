package com.shanshan.serverai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ServiceProcess {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    private static Process process;
    private static boolean owned;
    private static volatile boolean ready;

    private ServiceProcess() {}

    public static synchronized void ensureStarted() {
        if (!AiConfig.MANAGE_SERVICE.get()) {
            ready = healthy();
            return;
        }
        if (healthy()) {
            ready = true;
            LOGGER.info("[AI] local service already running");
            return;
        }
        if (process != null && process.isAlive()) {
            return;
        }

        try {
            Path dir = Path.of(AiConfig.SERVICE_DIR.get());
            Path entry = dir.resolve("server.js");
            if (!Files.isRegularFile(entry)) {
                LOGGER.warn("[AI] service entry not found: {}", entry);
                ready = false;
                return;
            }

            String auth = readAuth();
            if (auth.isEmpty()) {
                LOGGER.warn("[AI] service auth file is empty: {}", AiConfig.AUTH_FILE.get());
                ready = false;
                return;
            }

            String node = resolveNode();
            ProcessBuilder builder = new ProcessBuilder(node, "server.js");
            builder.directory(dir.toFile());
            builder.environment().put("MC_AI_" + "AUTH", auth);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("service.log").toFile()));
            process = builder.start();
            owned = true;
            ready = false;
            LOGGER.info("[AI] starting local service: {} {}", node, entry);

            Thread watcher = new Thread(ServiceProcess::awaitReady, "serverai-service-watch");
            watcher.setDaemon(true);
            watcher.start();
        } catch (Exception e) {
            LOGGER.error("[AI] failed to start local service", e);
            ready = false;
        }
    }

    public static void ensureStartedAsync() {
        Thread thread = new Thread(ServiceProcess::ensureStarted, "serverai-service-start");
        thread.setDaemon(true);
        thread.start();
    }

    private static void awaitReady() {
        for (int i = 0; i < 40; i++) {
            if (healthy()) {
                ready = true;
                LOGGER.info("[AI] local service ready");
                return;
            }
            Process current = process;
            if (current != null && !current.isAlive()) {
                break;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        ready = false;
        LOGGER.warn("[AI] local service did not become ready");
    }

    public static boolean isReady() {
        return ready;
    }

    public static synchronized void stop() {
        ready = false;
        if (process != null && owned) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        owned = false;
    }

    private static String readAuth() {
        try {
            return Files.readString(Path.of(AiConfig.AUTH_FILE.get()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveNode() {
        String configured = AiConfig.NODE_PATH.get().trim();
        if (!configured.isEmpty()) {
            return configured;
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = windows ? "node.exe" : "node";

        String path = System.getenv("PATH");
        if (path != null) {
            for (String part : path.split(File.pathSeparator)) {
                if (part.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(part, exe);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }

        String[] common = windows
                ? new String[]{"C:\\Program Files\\nodejs\\node.exe", "C:\\Program Files (x86)\\nodejs\\node.exe"}
                : new String[]{"/usr/local/bin/node", "/opt/homebrew/bin/node", "/usr/bin/node"};
        for (String candidate : common) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return exe;
    }

    private static boolean healthy() {
        try {
            String url = AiConfig.SERVICE_URL.get();
            int slash = url.lastIndexOf('/');
            String base = slash > 0 ? url.substring(0, slash) : "http://127.0.0.1:25580";
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        }
    }
}
