#!/data/data/com.termux/files/usr/bin/sh
cd "$(dirname "$0")"
termux-wake-lock
python -m http.server 8080 --directory site
