#!/usr/bin/env bash
# DIP AI — एक जगह से तीनों AI टूल (Gemini, Claude Code, Codex)
# Android (Termux), Linux और Mac पर चलता है।

TOOLS=(gemini claude codex)
NAMES=("Gemini (फ़्री)" "Claude Code" "Codex")

has() { command -v "$1" >/dev/null 2>&1; }

run_tool() { # $1 = tool, $2 = काम
  case "$1" in
    gemini) if [ -n "$2" ]; then gemini -i "$2"; else gemini; fi ;;
    claude) if [ -n "$2" ]; then claude "$2"; else claude; fi ;;
    codex)  if [ -n "$2" ]; then codex "$2";  else codex;  fi ;;
  esac
}

auto() { # पहले फ़्री Gemini, न चले तो अगला
  local task="$1" tried=0
  for i in 0 1 2; do
    t=${TOOLS[$i]}
    has "$t" || continue
    tried=1
    echo; echo "▶ ${NAMES[$i]} से काम करवा रहा हूँ..."; echo
    if run_tool "$t" "$task"; then return 0; fi
    echo; echo "⚠ ${NAMES[$i]} नहीं चला, अगला टूल आज़माता हूँ..."
  done
  [ $tried = 0 ] && echo "⚠ कोई AI टूल इंस्टॉल नहीं है। मेन्यू में 3 दबाकर इंस्टॉल करें।"
  return 1
}

install_tools() {
  echo; echo "📦 इंस्टॉल हो रहा है (इंटरनेट चाहिए)..."
  if [ -n "$TERMUX_VERSION" ] || [ -d /data/data/com.termux ]; then
    pkg update -y && pkg install -y nodejs-lts git
  elif ! has npm; then
    echo "⚠ पहले Node.js डालें: https://nodejs.org"; return 1
  fi
  npm install -g @google/gemini-cli && echo "✅ Gemini (फ़्री) तैयार"
  echo
  read -rp "Claude Code भी डालें? (पैसे वाला Claude प्लान चाहिए) [y/N]: " a
  [[ "$a" =~ ^[yY] ]] && npm install -g @anthropic-ai/claude-code
  read -rp "Codex भी डालें? (ChatGPT खाता चाहिए) [y/N]: " a
  [[ "$a" =~ ^[yY] ]] && npm install -g @openai/codex
  echo; echo "✅ हो गया। पहली बार टूल खुलने पर लॉगिन करें।"
}

status() {
  echo
  for i in 0 1 2; do
    if has "${TOOLS[$i]}"; then echo "  ✅ ${NAMES[$i]}"; else echo "  ❌ ${NAMES[$i]} (इंस्टॉल नहीं)"; fi
  done
}

# सीधे काम दिया हो: dip-ai "मेरी वेबसाइट में बटन जोड़ो"
if [ -n "$1" ]; then auto "$*"; exit $?; fi

while true; do
  echo
  echo "=============================="
  echo "     🤖 DIP AI — कमांड टूल"
  echo "=============================="
  status
  echo
  echo "  1) काम लिखें (अपने-आप सबसे अच्छा टूल)"
  echo "  2) खुद टूल चुनें"
  echo "  3) टूल इंस्टॉल करें"
  echo "  0) बाहर निकलें"
  read -rp "चुनें: " c
  case "$c" in
    1) read -rp "क्या काम करवाना है? " task; auto "$task" ;;
    2) echo "  1) ${NAMES[0]}   2) ${NAMES[1]}   3) ${NAMES[2]}"
       read -rp "टूल: " n
       t=${TOOLS[$((n-1))]}
       if [ -n "$t" ] && has "$t"; then
         read -rp "क्या काम करवाना है? (खाली छोड़ें तो सीधे खुलेगा) " task
         run_tool "$t" "$task"
       else echo "⚠ यह टूल इंस्टॉल नहीं है।"; fi ;;
    3) install_tools ;;
    0) exit 0 ;;
  esac
done
