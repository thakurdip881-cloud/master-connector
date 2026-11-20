#!/usr/bin/env bash
# DIP AI इंस्टॉलर — Termux / Linux / Mac
set -e
URL="https://thakurdip881-cloud.github.io/master-connector/ai/dip-ai.sh"
if [ -n "$PREFIX" ] && [ -d "$PREFIX/bin" ]; then BIN="$PREFIX/bin"; else BIN="$HOME/.local/bin"; mkdir -p "$BIN"; fi
curl -fsSL "$URL" -o "$BIN/dip-ai"
chmod +x "$BIN/dip-ai"
echo "✅ DIP AI डल गया। अब बस लिखें:  dip-ai"
"$BIN/dip-ai"
