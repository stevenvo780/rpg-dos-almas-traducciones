#!/usr/bin/env bash
# Compila el launcher y genera dist/DosAlmasLauncher.jar
# Uso:  ./build.sh          compila y empaqueta
#       ./build.sh check    compila y verifica la instalacion
set -euo pipefail

cd "$(dirname "$0")"
VERSION="0.1.0"
MAIN="com.dosalmas.launcher.Main"

echo ">> Compilando (--release 17 para maxima compatibilidad)..."
rm -rf build && mkdir -p build dist
javac --release 17 -encoding UTF-8 -d build $(find src -name '*.java')

echo ">> Empaquetando jar..."
cat > build/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Main-Class: $MAIN
Implementation-Title: Launcher Dos Almas
Implementation-Version: $VERSION
EOF
jar --create --file "dist/DosAlmasLauncher.jar" --manifest build/MANIFEST.MF -C build com

echo ">> Listo: dist/DosAlmasLauncher.jar ($(du -h dist/DosAlmasLauncher.jar | cut -f1))"

if [[ "${1:-}" == "check" ]]; then
    echo
    java -jar dist/DosAlmasLauncher.jar --check
fi
