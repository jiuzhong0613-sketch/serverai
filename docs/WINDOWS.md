# Windows Installation

This guide covers the Forge mod plus the local AI service on Windows 10/11.

## Requirements

- Java 17 for the Minecraft server
- Node.js 18 or newer
- Minecraft 1.20.1 + Forge 47.4.0 server
- A DeepSeek API key

## Directory layout

    C:\mc_server_1201      Minecraft server
    C:\mc_ai_service       AI service

## 1. Install the AI service

Copy the repository's <code>service</code> directory to <code>C:\mc_ai_service</code>.

Copy <code>config.example.json</code> to <code>config.json</code> and set:

    "serverRoot": "C:/mc_server_1201"

Leave <code>host</code> as <code>127.0.0.1</code>. Do not expose port 25580.

## 2. Create the key and auth files

Create the directory:

    mkdir "%USERPROFILE%\.config\mc-ai"

Create <code>deepseek.key</code> with Notepad and paste the API key as one line:

    notepad "%USERPROFILE%\.config\mc-ai\deepseek.key"

Generate the local service auth value:

    node -e "console.log(require('crypto').randomBytes(32).toString('hex'))" | Set-Content -Encoding ascii "%USERPROFILE%\.config\mc-ai\auth.txt"

The mod and the service must use the same <code>auth.txt</code>.

## 3. Start the service

    cd C:\mc_ai_service
    start-ai.bat

Check it:

    curl http://127.0.0.1:25580/health

Expected: <code>{"ok":true,...}</code>. If <code>keyConfigured</code> is false, the key file is missing or empty.

## 4. Configure the mod

Edit <code>world\serverconfig\serverai-server.toml</code> after stopping the server:

    [service]
    serviceUrl = "http://127.0.0.1:25580/chat"
    manageService = true
    serviceDir = "C:/mc_ai_service"
    authFile = "C:/Users/<you>/.config/mc-ai/auth.txt"
    nodePath = "C:/Program Files/nodejs/node.exe"
    timeoutSeconds = 185

<code>nodePath</code> is optional. Leave it empty to search PATH and common install locations. Set it explicitly if the server runs as a service and cannot find <code>node.exe</code>.

Restart the Minecraft server. The mod now starts <code>node server.js</code> directly; it does not use bash on Windows.

## 5. Firewall

Allow the game and skin ports from players:

    New-NetFirewallRule -DisplayName "Minecraft 25566" -Direction Inbound -Protocol TCP -LocalPort 25566 -Action Allow
    New-NetFirewallRule -DisplayName "Minecraft skins 25567" -Direction Inbound -Protocol TCP -LocalPort 25567 -Action Allow

Do not open 25580. It should stay local-only.

## 6. Logs

- Mod Q&A: <code>C:\mc_server_1201\logs\ai\YYYY-MM-DD.jsonl</code>
- Service metadata: <code>C:\mc_ai_service\logs\service-YYYY-MM-DD.jsonl</code>
- Service stdout: <code>C:\mc_ai_service\service.log</code>

## 7. Permissions

POSIX <code>600</code> does not apply on Windows. Restrict the config folder with NTFS:

    icacls "%USERPROFILE%\.config\mc-ai" /inheritance:r /grant:r "%USERNAME%:(OI)(CI)F"

## Notes

- The Java mod is cross-platform. File paths use <code>java.nio.file</code>.
- All AI file tools are restricted to <code>serverRoot</code>.
- Windows runtime has not been tested on this project's CI; the build runs on Ubuntu. If startup fails, check <code>service.log</code> first.
