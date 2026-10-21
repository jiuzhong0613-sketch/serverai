#!/bin/bash
set -e
cd "$(dirname "$0")"
AUTH_FILE="$HOME/.config/mc-ai/auth.txt"
if [ ! -s "$AUTH_FILE" ]; then
  echo "missing $AUTH_FILE" >&2
  exit 1
fi
export MC_AI_AUTH="$(cat "$AUTH_FILE")"
exec node server.js
