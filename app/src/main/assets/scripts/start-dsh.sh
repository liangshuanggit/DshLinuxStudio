#!/bin/bash
# 由 APP 的前台服务启动；API Key 通过进程环境变量注入。
set -euo pipefail
: "${DEEPSEEK_API_KEY:?请先在安装 APP 的设置页填写 DeepSeek API Key}"
exec dsh web --no-open --host 127.0.0.1 --port "${DSH_PORT:-3080}"
