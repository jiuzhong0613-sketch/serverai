# Server AI Service

Local bridge between the Forge mod and DeepSeek. It listens on 127.0.0.1:25580.

## Requirements

- Node.js 18 or newer
- A DeepSeek API key
- The Minecraft server directory on the same machine

## Setup

1. Copy config.example.json to config.json.
2. Set serverRoot to the Minecraft server directory.
3. Create the key file at ~/.config/mc-ai/deepseek.key (one line, the API key).
4. Create the auth file at ~/.config/mc-ai/auth.txt (one random line shared with the mod).
5. Start the service.

Linux / macOS:

    chmod +x start-ai.sh
    ./start-ai.sh

Windows:

    start-ai.bat

## Mod-managed startup

The 0.1.2+ mod can start the service directly:

- manageService = true
- serviceDir = path to this directory
- nodePath = optional absolute path to node.exe or node
- authFile = path to auth.txt

The mod runs node server.js and passes MC_AI_AUTH in the environment. It does not use bash on Windows.

## Platform notes

- Linux: see ../docs/LINUX.md
- Windows: see ../docs/WINDOWS.md
- macOS: the same start-ai.sh works; the mod also starts it directly.

## Paths

- config.json: local settings (not committed)
- logs/service-YYYY-MM-DD.jsonl: request metadata, no question text
- service.log: stdout and errors
- state.json: daily counter (not committed)

## Security

- Keep config.json, auth.txt and the API key out of git.
- The HTTP server binds to 127.0.0.1 only.
- File tools are restricted to serverRoot; secrets are redacted.
