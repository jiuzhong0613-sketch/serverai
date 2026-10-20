package com.shanshan.serverai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Stream;

public final class ChatLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static Path dir;

    private ChatLog() {}

    public static synchronized void start(MinecraftServer server) {
        dir = server.getServerDirectory().toPath().resolve("logs").resolve("ai");
        try {
            Files.createDirectories(dir);
            try {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
            } catch (Exception ignored) {
            }
            cleanup();
        } catch (IOException e) {
            LOGGER.warn("[AI] cannot create log directory", e);
        }
    }

    private static void cleanup() {
        try (Stream<Path> stream = Files.list(dir)) {
            long cutoff = System.currentTimeMillis() - 30L * 24L * 3600L * 1000L;
            stream.filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.jsonl"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoff;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public static synchronized void log(String player, UUID uuid, String ip, boolean op, String requestId,
                                        String question, String answer, String status,
                                        long latencyMs, String error, JsonElement usage) {
        if (dir == null) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("ts", OffsetDateTime.now().toString());
        entry.addProperty("requestId", requestId);
        entry.addProperty("player", player);
        entry.addProperty("uuid", uuid.toString());
        entry.addProperty("ip", ip);
        entry.addProperty("op", op);
        entry.addProperty("question", question);
        entry.addProperty("answer", answer);
        entry.addProperty("status", status);
        entry.addProperty("latencyMs", latencyMs);
        if (error != null) {
            entry.addProperty("error", error);
        }
        if (usage != null) {
            entry.add("usage", usage);
        }

        Path file = dir.resolve(LocalDate.now().format(DATE) + ".jsonl");
        try {
            Files.writeString(file, GSON.toJson(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) {
            }
        } catch (IOException e) {
            LOGGER.warn("[AI] cannot write chat log", e);
        }

        LOGGER.info("[AI] {} asked {} chars, answered {} chars, status={}, {} ms",
                player, question.length(), answer.length(), status, latencyMs);
    }
}
