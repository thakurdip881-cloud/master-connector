#!/usr/bin/env bash
# DIP AI — एक जगह से तीनों AI टूल (Gemini, Claude Code, Codex)
# Android (Termux), Linux और Mac पर चलता है।
# 📋 कॉपी-पेस्ट: कॉपी किया हुआ text अपने-आप काम बनता है, और जवाब अपने-आप कॉपी होता है।

TOOLS=(gemini claude codex)
NAMES=("Gemini (फ़्री)" "Claude Code" "Codex")

has() { command -v "$1" >/dev/null 2>&1; }

# ---------- 📋 क्लिपबोर्ड ----------
clip_get() {
  if has termux-clipboard-get; then termux-clipboard-get
  elif has pbpaste; then pbpaste
  elif has wl-paste; then wl-paste -n
  elif has xclip; then xclip -o -selection clipboard
  else return 1; fi
}
clip_set() {
  if has termux-clipboard-set; then termux-clipboard-set
  elif has pbcopy; then pbcopy
  elif has wl-copy; then wl-copy
  elif has xclip; then xclip -selection clipboard
  else cat >/dev/null; return 1; fi
}
clip_ok() { has termux-clipboard-get || has pbpaste || has wl-paste || has xclip; }
clip_help() {
  echo "⚠ कॉपी-पेस्ट अभी चालू नहीं है।"
  echo "  Termux में: Play Store/F-Droid से 'Termux:API' ऐप डालें, फिर मेन्यू में 3 दबाएँ।"
}

# ---------- AI चलाना ----------
run_tool() { # बातचीत वाला मोड: $1 = tool, $2 = काम
  case "$1" in
    gemini) if [ -n "$2" ]; then gemini -i "$2"; else gemini; fi ;;
    claude) if [ -n "$2" ]; then claude "$2"; else claude; fi ;;
    codex)  if [ -n "$2" ]; then codex "$2";  else codex;  fi ;;
  esac
}
ask_tool() { # सीधा जवाब वाला मोड (जवाब कॉपी करने के लिए)
  case "$1" in
    gemini) gemini -p "$2" ;;
    claude) claude -p "$2" ;;
    codex)  codex exec "$2" ;;
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

auto_answer_copy() { # सवाल → जवाब → अपने-आप कॉपी
  local task="$1" out
  for i in 0 1 2; do
    t=${TOOLS[$i]}
    has "$t" || continue
    echo; echo "▶ ${NAMES[$i]} से जवाब ले रहा हूँ..."; echo
    if out=$(ask_tool "$t" "$task") && [ -n "$out" ]; then
      echo "$out"; echo
      if printf '%s' "$out" | clip_set; then
        echo "📋 जवाब कॉपी हो गया — अब किसी भी ऐप में लंबा दबाकर 'Paste' करें।"
      else clip_help; fi
      return 0
    fi
    echo "⚠ ${NAMES[$i]} नहीं चला, अगला टूल आज़माता हूँ..."
  done
  echo "⚠ कोई AI टूल नहीं चला। मेन्यू में 3 दबाकर इंस्टॉल करें।"
  return 1
}

from_clipboard() { # कॉपी किया हुआ text = काम
  local task
  if ! clip_ok; then clip_help; return 1; fi
  task=$(clip_get)
  if [ -z "$task" ]; then echo "⚠ कुछ कॉपी नहीं किया हुआ है। पहले कोई text कॉपी करें।"; return 1; fi
  echo; echo "📋 कॉपी किया हुआ काम:"; echo "$task" | head -5; echo
  auto_answer_copy "$task"
}

install_tools() {
  echo; echo "📦 इंस्टॉल हो रहा है (इंटरनेट चाहिए)..."
  if [ -n "$TERMUX_VERSION" ] || [ -d /data/data/com.termux ]; then
    pkg update -y && pkg install -y nodejs-lts git termux-api
    echo "ℹ कॉपी-पेस्ट के लिए 'Termux:API' ऐप भी फ़ोन में होना चाहिए (फ़्री)।"
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
  if clip_ok; then echo "  ✅ कॉपी-पेस्ट"; else echo "  ❌ कॉपी-पेस्ट (Termux:API चाहिए)"; fi
}

# सीधे चलाना:
#   dip-ai "काम"     → बातचीत वाला मोड
#   dip-ai --copy    → कॉपी किया हुआ text से काम, जवाब अपने-आप कॉपी
if [ "$1" = "--copy" ]; then from_clipboard; exit $?; fi
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
  echo "  4) 📋 कॉपी किया हुआ text से काम (जवाब अपने-आप कॉपी)"
  echo "  5) 📋 सवाल लिखें, जवाब अपने-आप कॉपी"
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
    4) from_clipboard ;;
    5) read -rp "सवाल/काम: " task; auto_answer_copy "$task" ;;
    0) exit 0 ;;
  esac
done
