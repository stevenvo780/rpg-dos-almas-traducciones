#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
stamp=$(date +%Y-%m-%d_%H-%M-%S)
mkdir -p backups
tar -czf "backups/Dos-Almas-$stamp.tar.gz" Dos-Almas server.properties config defaultconfigs 2>/dev/null || true
find backups -type f -name 'Dos-Almas-*.tar.gz' -mtime +14 -delete
