package com.shanshan.serverai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AiService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Map<UUID, PlayerState> PLAYERS = new ConcurrentHashMap<>();

    private static MinecraftServer server;
    private static ThreadPoolExecutor executor;
    private static ScheduledExecutorService scheduler;
    private static String dailyKey = "";
    private static int dailyCount;

    private AiService() {}

    public static synchronized void start(MinecraftServer s) {
        server = s;
        if (executor == null) {
            executor = new ThreadPoolExecutor(
                    3, 3, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(10),
                    r -> {
                        Thread t = new Thread(r, "serverai-http");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
        }
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "serverai-chat");
                t.setDaemon(true);
                return t;
            });
        }
        ChatLog.start(s);
        ServiceProcess.ensureStarted();
    }

    public static synchronized void stop() {
        server = null;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        PLAYERS.clear();
        ServiceProcess.stop();
    }

    public static void ask(ServerPlayer player, String raw) {
        if (server == null) {
            player.sendSystemMessage(Component.literal("§c[AI] 服务未就绪"));
            return;
        }
        String question = raw == null ? "" : raw.trim();
        if (question.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[AI] 用法：§f/ai <问题>"));
            return;
        }
        int maxQuestion = AiConfig.MAX_QUESTION_CHARS.get();
        if (question.length() > maxQuestion) {
            player.sendSystemMessage(Component.literal("§c[AI] 问题太长了，最多 " + maxQuestion + " 个字符"));
            return;
        }
        if (!ServiceProcess.isReady()) {
            ServiceProcess.ensureStartedAsync();
            player.sendSystemMessage(Component.literal("§7[AI] 服务正在启动，请几秒后再试"));
            return;
        }

        long now = System.currentTimeMillis();
        int cooldown = AiConfig.COOLDOWN_SECONDS.get();
        int perMinute = AiConfig.MAX_PER_MINUTE.get();
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), k -> new PlayerState());
        synchronized (state) {
            long waitMs = state.lastMs + cooldown * 1000L - now;
            if (waitMs > 0) {
                player.sendSystemMessage(Component.literal("§c[AI] 慢一点，还要等 " + ((waitMs + 999) / 1000) + " 秒"));
                return;
            }
            if (now - state.minuteStart >= 60_000L) {
                state.minuteStart = now;
                state.minuteCount = 0;
            }
            if (state.minuteCount >= perMinute) {
                player.sendSystemMessage(Component.literal("§c[AI] 每分钟最多问 " + perMinute + " 次"));
                return;
            }
            state.lastMs = now;
            state.minuteCount++;
        }

        if (!consumeDaily()) {
            player.sendSystemMessage(Component.literal("§c[AI] 今天的次数用完了，请联系服主"));
            return;
        }

        if (!player.getPersistentData().getBoolean("serverai_notice")) {
            player.getPersistentData().putBoolean("serverai_notice", true);
            player.sendSystemMessage(Component.literal(
                    "§7[AI] 你的问题会发送到 DeepSeek API，并在服务器日志中记录（含玩家名和 IP）。回答仅供参考。"));
        }

        String auth = readAuth();
        if (auth.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[AI] 服务未配置，请联系服主"));
            return;
        }

        String name = player.getGameProfile().getName();
        UUID uuid = player.getUUID();
        String ip = player.getIpAddress();
        String requestId = UUID.randomUUID().toString();

        JsonObject payload = new JsonObject();
        payload.addProperty("id", requestId);
        payload.addProperty("question", question);
        String body = payload.toString();

        player.sendSystemMessage(Component.literal("§7[AI] 正在思考…"));
        try {
            executor.execute(() -> request(player, name, uuid, ip, requestId, question, body, auth));
        } catch (RejectedExecutionException e) {
            player.sendSystemMessage(Component.literal("§c[AI] 当前排队已满，请稍后再试"));
        }
    }

    private static void request(ServerPlayer player, String name, UUID uuid, String ip,
                                String requestId, String question, String body, String auth) {
        long started = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(AiConfig.SERVICE_URL.get()))
                    .timeout(Duration.ofSeconds(AiConfig.TIMEOUT_SECONDS.get()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bear" + "er " + auth)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = System.currentTimeMillis() - started;

            if (response.statusCode() != 200) {
                String error = parseError(response.body());
                ChatLog.log(name, uuid, ip, requestId, question, "", "error", latency, error, null);
                send(player, "§c[AI] " + friendly(response.statusCode(), error));
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String answer = json.has("answer") ? json.get("answer").getAsString().trim() : "";
            JsonElement usage = json.get("usage");
            if (answer.isEmpty()) {
                ChatLog.log(name, uuid, ip, requestId, question, "", "empty", latency, "empty answer", usage);
                send(player, "§c[AI] 没有拿到回答，请稍后再试");
                return;
            }
            ChatLog.log(name, uuid, ip, requestId, question, answer, "ok", latency, null, usage);
            deliver(player, answer);
        } catch (HttpTimeoutException e) {
            long latency = System.currentTimeMillis() - started;
            ChatLog.log(name, uuid, ip, requestId, question, "", "timeout", latency, "timeout", null);
            send(player, "§c[AI] 超时了，请稍后再试");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            LOGGER.warn("[AI] request failed", e);
            ChatLog.log(name, uuid, ip, requestId, question, "", "error", latency, e.getMessage(), null);
            send(player, "§c[AI] 服务暂时不可用，请稍后再试");
        }
    }

    private static String readAuth() {
        try {
            return Files.readString(Path.of(AiConfig.AUTH_FILE.get()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String parseError(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return json.has("error") ? json.get("error").getAsString() : body;
        } catch (Exception e) {
            return body;
        }
    }

    private static String friendly(int code, String error) {
        if (code == 429 && "busy".equals(error)) {
            return "当前排队已满，请稍后再试";
        }
        if (code == 429) {
            return "今天的 AI 次数用完了，请联系服主";
        }
        if (code == 503) {
            return "AI 服务未配置，请联系服主";
        }
        if (code == 504) {
            return "AI 响应超时，请稍后再试";
        }
        if (code == 401) {
            return "AI 服务认证失败，请联系服主";
        }
        return "AI 暂时不可用，请稍后再试";
    }

    private static void deliver(ServerPlayer player, String answer) {
        List<String> chunks = split(answer, AiConfig.MAX_CHUNKS.get(), AiConfig.CHUNK_CHARS.get());
        int delay = AiConfig.CHUNK_DELAY_MS.get();
        for (int i = 0; i < chunks.size(); i++) {
            String line = "§b[AI] §f" + chunks.get(i);
            long wait = (long) i * delay;
            if (wait <= 0) {
                send(player, line);
            } else {
                ScheduledExecutorService scheduled = scheduler;
                if (scheduled != null) {
                    scheduled.schedule(() -> send(player, line), wait, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    static List<String> split(String text, int maxChunks, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String clean = text.replace("\r", "")
                .replaceAll("[\\t\\f]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .replace("**", "")
                .replaceAll("\\x60", "")
                .replace("§", "")
                .trim();
        int index = 0;
        while (index < clean.length() && out.size() < maxChunks) {
            int end = Math.min(clean.length(), index + width);
            if (end < clean.length()) {
                int cut = clean.lastIndexOf('\n', end);
                cut = Math.max(cut, clean.lastIndexOf('。', end));
                cut = Math.max(cut, clean.lastIndexOf('！', end));
                cut = Math.max(cut, clean.lastIndexOf('？', end));
                if (cut > index + width / 3) {
                    end = cut + 1;
                }
            }
            String part = clean.substring(index, end).trim();
            if (!part.isEmpty()) {
                out.add(part);
            }
            index = end;
        }
        if (index < clean.length() && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, out.get(last) + "…");
        }
        return out;
    }

    private static synchronized boolean consumeDaily() {
        String today = LocalDate.now().toString();
        if (!today.equals(dailyKey)) {
            dailyKey = today;
            dailyCount = 0;
        }
        if (dailyCount >= AiConfig.DAILY_LIMIT.get()) {
            return false;
        }
        dailyCount++;
        return true;
    }

    private static void send(ServerPlayer player, String message) {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        try {
            current.execute(() -> {
                ServerPlayer online = current.getPlayerList().getPlayer(player.getUUID());
                if (online != null) {
                    online.sendSystemMessage(Component.literal(message));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static final class PlayerState {
        long lastMs;
        long minuteStart;
        int minuteCount;
    }
}
