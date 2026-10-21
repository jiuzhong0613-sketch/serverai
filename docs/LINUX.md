# Linux Installation

This guide covers the Forge mod plus the local AI service on a Linux server.

## Requirements

- Java 17
- Node.js 18 or newer
- Minecraft 1.20.1 + Forge 47.4.0 server
- A DeepSeek API key

## Directory layout

    /srv/mc_server_1201      Minecraft server
    /srv/mc_ai_service       AI service

Run the service as the same user that owns the Minecraft server, or make sure both can read the server directory.

## 1. Install the AI service

    sudo mkdir -p /srv/mc_ai_service
    sudo chown "$USER":"$USER" /srv/mc_ai_service
    cp -r service/* /srv/mc_ai_service/
    cd /srv/mc_ai_service
    cp config.example.json config.json

Edit <code>config.json</code>:

    "serverRoot": "/srv/mc_server_1201"

Keep <code>host</code> as <code>127.0.0.1</code>.

## 2. Create the key and auth files

    mkdir -p "$HOME/.config/mc-ai"
    chmod 700 "$HOME/.config/mc-ai"

Create <code>deepseek.key</code> with one line (the API key):

    nano "$HOME/.config/mc-ai/deepseek.key"

Create the local auth value:

    node -e "console.log(require('crypto').randomBytes(32).toString('hex'))" > "$HOME/.config/mc-ai/auth.txt"

    chmod 600 "$HOME/.config/mc-ai/deepseek.key" "$HOME/.config/mc-ai/auth.txt"

## 3. Start the service

    cd /srv/mc_ai_service
    chmod +x start-ai.sh
    ./start-ai.sh

Check it:

    curl http://127.0.0.1:25580/health

## 4. Configure the mod

Edit <code>world/serverconfig/serverai-server.toml</code> after stopping the server:

    [service]
    serviceUrl = "http://127.0.0.1:25580/chat"
    manageService = true
    serviceDir = "/srv/mc_ai_service"
    authFile = "/home/<user>/.config/mc-ai/auth.txt"
    nodePath = "/usr/bin/node"
    timeoutSeconds = 185

<code>nodePath</code> is optional. The mod searches PATH and common locations if it is empty.

Restart the Minecraft server. The mod starts <code>node server.js</code> directly.

## 5. Optional systemd

If you prefer systemd instead of mod-managed startup, set <code>manageService = false</code> and use:

    [Unit]
    Description=Server AI service
    After=network-online.target
    Wants=network-online.target

    [Service]
    User=mc
    WorkingDirectory=/srv/mc_ai_service
    ExecStart=/bin/bash /srv/mc_ai_service/start-ai.sh
    Restart=on-failure

    [Install]
    WantedBy=multi-user.target

Save as <code>/etc/systemd/system/serverai.service</code>, then:

    sudo systemctl daemon-reload
    sudo systemctl enable --now serverai

Do not run both systemd and <code>manageService = true</code> at the same time.

## 6. Firewall

With ufw:

    sudo ufw allow 25566/tcp
    sudo ufw allow 25567/tcp

Do not open 25580. It should stay on loopback.

## 7. Logs

- Mod Q&A: <code>/srv/mc_server_1201/logs/ai/YYYY-MM-DD.jsonl</code>
- Service metadata: <code>/srv/mc_ai_service/logs/service-YYYY-MM-DD.jsonl</code>
- Service stdout: <code>/srv/mc_ai_service/service.log</code>

## Notes

- The service uses <code>realpath</code> to keep file tools inside <code>serverRoot</code>.
- Keep <code>config.json</code>, <code>auth.txt</code> and the API key out of git.
- For a public server, put the game behind ZeroTier or a firewall; never expose 25580.
