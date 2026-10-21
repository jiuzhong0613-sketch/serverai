package com.shanshan.serverai;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AiConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> SERVICE_URL;
    public static final ForgeConfigSpec.BooleanValue MANAGE_SERVICE;
    public static final ForgeConfigSpec.ConfigValue<String> SERVICE_DIR;
    public static final ForgeConfigSpec.ConfigValue<String> AUTH_FILE;
    public static final ForgeConfigSpec.ConfigValue<String> NODE_PATH;
    public static final ForgeConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue MAX_QUESTION_CHARS;
    public static final ForgeConfigSpec.IntValue DAILY_LIMIT;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS;
    public static final ForgeConfigSpec.IntValue CHUNK_CHARS;
    public static final ForgeConfigSpec.IntValue CHUNK_DELAY_MS;
    public static final ForgeConfigSpec SPEC;

    static {
        B.comment("Local AI bridge. Keep it on 127.0.0.1.").push("service");
        SERVICE_URL = B.define("serviceUrl", "http://127.0.0.1:25580/chat");
        MANAGE_SERVICE = B.comment("Start and stop the local AI service with the Minecraft server.")
                .define("manageService", true);
        SERVICE_DIR = B.define("serviceDir", System.getProperty("user.home") + "/mc_ai_service");
        AUTH_FILE = B.define("authFile", System.getProperty("user.home") + "/.config/mc-ai/auth.txt");
        NODE_PATH = B.comment("Node executable. Leave empty to search PATH and common locations.")
                .define("nodePath", "");
        TIMEOUT_SECONDS = B.comment("HTTP timeout in seconds. Must exceed the bridge timeout (180s).")
                .defineInRange("timeoutSeconds", 185, 10, 300);
        B.pop();

        B.comment("Per-player limits.").push("limits");
        COOLDOWN_SECONDS = B.defineInRange("cooldownSeconds", 15, 0, 3600);
        MAX_PER_MINUTE = B.defineInRange("maxPerMinute", 3, 1, 60);
        MAX_QUESTION_CHARS = B.defineInRange("maxQuestionChars", 250, 1, 250);
        DAILY_LIMIT = B.defineInRange("dailyLimit", 1000, 1, 100000);
        B.pop();

        B.comment("Chat display.").push("display");
        MAX_CHUNKS = B.defineInRange("maxChunks", 3, 1, 10);
        CHUNK_CHARS = B.defineInRange("chunkChars", 180, 40, 250);
        CHUNK_DELAY_MS = B.defineInRange("chunkDelayMs", 250, 0, 5000);
        B.pop();

        SPEC = B.build();
    }

    private AiConfig() {}
}
