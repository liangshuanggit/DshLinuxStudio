#!/bin/bash
# 在 APP 内安装的 Ubuntu PRoot 环境中安装 DeepSeek Harness。
set -euo pipefail

echo "==> 检查 Node.js"
node --version
npm --version

echo "==> 安装 @deepseek-ai/dsh"
npm install -g @deepseek-ai/dsh@latest --registry=https://registry.npmjs.org

echo "==> 验证"
dsh --version
