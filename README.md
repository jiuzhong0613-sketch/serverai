# Server AI

Minecraft 1.20.1 Forge 47.4.0 服务端模组：玩家用 /ai <问题> 向 DeepSeek 提问，回答只发给提问者。

## 组成

- 本仓库：Forge 服务端模组
- 本地 AI 服务：~/mc_ai_service（Node，监听 127.0.0.1:25580）
- 密钥与本地鉴权：~/.config/mc-ai/（不进仓库）

## 构建

GitHub Actions 自动构建，产物在 Actions 的 serverai-jar artifact 里。

本地构建需要 Java 17：

    export JAVA_HOME="$HOME/java/jdk-17/Contents/Home"
    ./gradlew build --no-daemon

## 安装

把 build/libs/serverai-0.1.0.jar 放进服务端 mods/，重启服务器。

模组会在服务器启动时拉起 ~/mc_ai_service/start-ai.sh，关服时关闭它。
配置文件：world/serverconfig/serverai-server.toml（关服后修改）。

## 命令

- /ai <问题>：提问，回答仅自己可见
- /ai help：帮助

## 日志

服务端日志：logs/ai/YYYY-MM-DD.jsonl
字段：ts、requestId、player、uuid、ip、question、answer、status、latencyMs、usage。
保留 30 天，权限 600。
