#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
exec ./java17/bin/java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.20/unix_args.txt nogui
