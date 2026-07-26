#!/usr/bin/env bash
# Genera todos los paquetes/instaladores de RPG Dos Almas Launcher que sea
# posible construir en esta maquina (Linux x86_64), dejando los artefactos
# en dist/. Pasos 1 y 2 (compilar + tarballs portables) son obligatorios;
# el resto (AppImage, .deb, .rpm) se intenta "en el mejor esfuerzo": si algo
# no se puede construir de forma limpia aqui, se avisa con claridad y se
# entrega en su lugar lo necesario para construirlo en la distro destino.
set -euo pipefail

cd "$(dirname "$0")"
RAIZ="$(pwd)"
PKG="$RAIZ/packaging/linux"
DIST="$RAIZ/dist"
HERRAMIENTAS="$RAIZ/packaging/tools"
ESCENARIO="$RAIZ/empaquetado-tmp"

NOMBRE_JAR="DosAlmasLauncher"
NOMBRE_PKG="dosalmas-launcher"
ARCH="x86_64"

# La version vive en un solo sitio (build.sh); aqui solo se lee.
VERSION="$(grep -m1 '^VERSION=' build.sh | cut -d'"' -f2)"
if [[ -z "$VERSION" ]]; then
    echo "Error: no se pudo leer VERSION desde build.sh" >&2
    exit 1
fi

echo "=================================================================="
echo " Empaquetando RPG Dos Almas Launcher v$VERSION ($ARCH)"
echo "=================================================================="

FALLOS=()

# ---------------------------------------------------------- Paso 1: compilar

echo
echo ">>> Paso 1/5: compilando el launcher (build.sh)"
./build.sh

JAR_ORIGEN="$DIST/${NOMBRE_JAR}.jar"
[[ -f "$JAR_ORIGEN" ]] || { echo "Error: no se genero $JAR_ORIGEN" >&2; exit 1; }
echo "    jar: $JAR_ORIGEN ($(du -h "$JAR_ORIGEN" | cut -f1))"

rm -rf "$ESCENARIO"
mkdir -p "$ESCENARIO" "$DIST" "$HERRAMIENTAS"

# Genera un README.txt para una carpeta de salida, con o sin nota de JRE
# incluido (usa sed con 'r' para insertar el bloque y luego borra la marca).
generar_readme() {
    local destino="$1" bloque="$2"
    sed -e "s/@VERSION@/$VERSION/g" \
        -e "/@JRE_NOTA@/r $bloque" \
        -e "/@JRE_NOTA@/d" \
        "$PKG/README.txt.in" > "$destino"
}

# Genera un .desktop a partir de la plantilla, sustituyendo Exec/Icon/Version.
generar_desktop() {
    local destino="$1" exec_val="$2" icon_val="$3"
    sed -e "s/@VERSION@/$VERSION/g" \
        -e "s|@EXEC@|$exec_val|g" \
        -e "s|@ICON@|$icon_val|g" \
        "$PKG/dosalmas-launcher.desktop.in" > "$destino"
}

# --------------------------------------------------- Paso 2: tarballs (linux)

echo
echo ">>> Paso 2/5: tarball portable universal"

STAGE_BASE="$ESCENARIO/tarball-basico/${NOMBRE_JAR}-${VERSION}"
mkdir -p "$STAGE_BASE"
cp "$JAR_ORIGEN" "$STAGE_BASE/${NOMBRE_JAR}.jar"
cp "$PKG/dosalmas-launcher.sh" "$STAGE_BASE/"
cp "$PKG/instalar.sh" "$STAGE_BASE/"
chmod +x "$STAGE_BASE/dosalmas-launcher.sh" "$STAGE_BASE/instalar.sh"
cp "$PKG/icono-256.png" "$STAGE_BASE/dosalmas-launcher.png"
generar_desktop "$STAGE_BASE/dosalmas-launcher.desktop" "/opt/dosalmas-launcher/dosalmas-launcher.sh" "dosalmas-launcher"
generar_readme "$STAGE_BASE/README.txt" "$PKG/README-nota-sin-jre.txt"

TARBALL_BASICO="$DIST/${NOMBRE_JAR}-${VERSION}-linux-${ARCH}.tar.gz"
tar -C "$ESCENARIO/tarball-basico" -czf "$TARBALL_BASICO" "${NOMBRE_JAR}-${VERSION}"
echo "    listo: $TARBALL_BASICO ($(du -h "$TARBALL_BASICO" | cut -f1))"

echo
echo ">>> Paso 2b/5: tarball con JRE embebido (variante -con-java)"
STAGE_JRE="$ESCENARIO/tarball-jre/${NOMBRE_JAR}-${VERSION}"
mkdir -p "$(dirname "$STAGE_JRE")"
cp -r "$STAGE_BASE" "$STAGE_JRE"
generar_desktop "$STAGE_JRE/dosalmas-launcher.desktop" "/opt/dosalmas-launcher/dosalmas-launcher.sh" "dosalmas-launcher"
generar_readme "$STAGE_JRE/README.txt" "$PKG/README-nota-con-jre.txt"
if [[ -d "$RAIZ/data/jvm/java-runtime-gamma" ]]; then
    cp -a "$RAIZ/data/jvm/java-runtime-gamma" "$STAGE_JRE/jre"
else
    echo "Error: no se encontro data/jvm/java-runtime-gamma para la variante con-java" >&2
    exit 1
fi

TARBALL_JRE="$DIST/${NOMBRE_JAR}-${VERSION}-linux-${ARCH}-con-java.tar.gz"
tar -C "$ESCENARIO/tarball-jre" -czf "$TARBALL_JRE" "${NOMBRE_JAR}-${VERSION}"
echo "    listo: $TARBALL_JRE ($(du -h "$TARBALL_JRE" | cut -f1))"

# ------------------------------------------------------ Paso 3/5: AppImage

echo
echo ">>> Paso 3/5: AppImage"

construir_appimage() {
    local herramienta="$HERRAMIENTAS/appimagetool-x86_64.AppImage"

    if [[ ! -x "$herramienta" ]]; then
        if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
            echo "    aviso: no hay 'curl' ni 'wget' para descargar appimagetool; se omite AppImage." >&2
            return 1
        fi
        echo "    descargando appimagetool a $herramienta ..."
        local url="https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage"
        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 2 --max-time 120 -o "$herramienta.tmp" "$url" || return 1
        else
            wget -q --tries=2 --timeout=120 -O "$herramienta.tmp" "$url" || return 1
        fi
        mv "$herramienta.tmp" "$herramienta"
        chmod +x "$herramienta"
    fi

    local appdir="$ESCENARIO/AppDir"
    rm -rf "$appdir"
    mkdir -p "$appdir"
    cp "$JAR_ORIGEN" "$appdir/${NOMBRE_JAR}.jar"
    cp "$PKG/dosalmas-launcher.sh" "$appdir/AppRun"
    chmod +x "$appdir/AppRun"
    cp "$PKG/icono-256.png" "$appdir/dosalmas-launcher.png"
    generar_desktop "$appdir/dosalmas-launcher.desktop" "dosalmas-launcher" "dosalmas-launcher"

    local salida="$DIST/${NOMBRE_JAR}-${VERSION}-${ARCH}.AppImage"
    rm -f "$salida"

    # --appimage-extract-and-run evita depender de FUSE para *construir* el
    # AppImage (el archivo resultante si necesita FUSE, o su propio
    # --appimage-extract-and-run, para *ejecutarse* mas adelante).
    if ARCH="$ARCH" "$herramienta" --appimage-extract-and-run "$appdir" "$salida" >"$ESCENARIO/appimagetool.log" 2>&1; then
        chmod +x "$salida"
        echo "    listo: $salida ($(du -h "$salida" | cut -f1))"
        return 0
    else
        echo "    aviso: appimagetool fallo. Log:" >&2
        tail -n 20 "$ESCENARIO/appimagetool.log" >&2 || true
        return 1
    fi
}

if ! construir_appimage; then
    echo "    AppImage NO generado en esta maquina (ver aviso arriba)." >&2
    FALLOS+=("AppImage")
fi

# ---------------------------------------------------------- Paso 4/5: .deb

echo
echo ">>> Paso 4/5: paquete .deb (Ubuntu/Debian)"

construir_deb() {
    if ! command -v ar >/dev/null 2>&1 || ! command -v tar >/dev/null 2>&1; then
        echo "    aviso: falta 'ar' o 'tar'; no se puede construir el .deb a mano." >&2
        return 1
    fi

    local root="$ESCENARIO/deb-root"
    rm -rf "$root"
    mkdir -p "$root/DEBIAN"
    mkdir -p "$root/usr/share/dosalmas-launcher"
    mkdir -p "$root/usr/bin"
    mkdir -p "$root/usr/share/applications"
    mkdir -p "$root/usr/share/icons/hicolor/256x256/apps"
    mkdir -p "$root/usr/share/doc/dosalmas-launcher"

    install -m 644 "$JAR_ORIGEN" "$root/usr/share/dosalmas-launcher/${NOMBRE_JAR}.jar"
    install -m 755 "$PKG/dosalmas-launcher.sh" "$root/usr/share/dosalmas-launcher/dosalmas-launcher.sh"
    ln -sf "../share/dosalmas-launcher/dosalmas-launcher.sh" "$root/usr/bin/dosalmas-launcher"
    generar_desktop "$root/usr/share/applications/dosalmas-launcher.desktop" "dosalmas-launcher" "dosalmas-launcher"
    install -m 644 "$PKG/icono-256.png" "$root/usr/share/icons/hicolor/256x256/apps/dosalmas-launcher.png"
    generar_readme "$root/usr/share/doc/dosalmas-launcher/README.txt" "$PKG/README-nota-sin-jre.txt"

    sed -e "s/@VERSION@/$VERSION/g" "$PKG/deb/control.in" > "$root/DEBIAN/control"
    install -m 755 "$PKG/deb/postinst" "$root/DEBIAN/postinst"
    install -m 755 "$PKG/deb/postrm" "$root/DEBIAN/postrm"

    # Tamano instalado (Kb), campo recomendado por la politica de Debian.
    local tam_kb
    tam_kb="$(du -sk "$root/usr" | cut -f1)"
    printf 'Installed-Size: %s\n' "$tam_kb" >> "$root/DEBIAN/control"

    local trabajo="$ESCENARIO/deb-build"
    rm -rf "$trabajo"
    mkdir -p "$trabajo"

    echo "2.0" > "$trabajo/debian-binary"

    tar --numeric-owner --owner=0 --group=0 -C "$root/DEBIAN" -czf "$trabajo/control.tar.gz" .
    tar --numeric-owner --owner=0 --group=0 -C "$root" --exclude=DEBIAN -czf "$trabajo/data.tar.gz" .

    local salida="$DIST/${NOMBRE_PKG}_${VERSION}_all.deb"
    rm -f "$salida"
    (cd "$trabajo" && ar rc "$salida" debian-binary control.tar.gz data.tar.gz)
    echo "    listo: $salida ($(du -h "$salida" | cut -f1))"
    echo "    (construido a mano con ar+tar; no se verifico con dpkg-deb porque no esta instalado aqui)"
    return 0
}

if ! construir_deb; then
    echo "    .deb NO generado en esta maquina (ver aviso arriba)." >&2
    FALLOS+=(".deb")
fi

# ---------------------------------------------------------- Paso 5/5: .rpm

echo
echo ">>> Paso 5/5: paquete .rpm (Fedora)"

construir_rpm() {
    if ! command -v rpmbuild >/dev/null 2>&1; then
        echo "    aviso: 'rpmbuild' no esta instalado; se omite la construccion del .rpm." >&2
        return 1
    fi

    local topdir="$ESCENARIO/rpmbuild"
    rm -rf "$topdir"
    mkdir -p "$topdir"/{BUILD,RPMS,SOURCES,SPECS,SRPMS,BUILDROOT}

    cp "$JAR_ORIGEN" "$topdir/SOURCES/${NOMBRE_JAR}.jar"
    cp "$PKG/dosalmas-launcher.sh" "$topdir/SOURCES/dosalmas-launcher.sh"
    cp "$PKG/icono-256.png" "$topdir/SOURCES/dosalmas-launcher.png"
    generar_desktop "$topdir/SOURCES/dosalmas-launcher.desktop" "dosalmas-launcher" "dosalmas-launcher"
    generar_readme "$topdir/SOURCES/README.txt" "$PKG/README-nota-sin-jre.txt"
    sed -e "s/@VERSION@/$VERSION/g" "$PKG/rpm/dosalmas-launcher.spec.in" > "$topdir/SPECS/dosalmas-launcher.spec"

    if ! rpmbuild --define "_topdir $topdir" -bb "$topdir/SPECS/dosalmas-launcher.spec" \
            >"$ESCENARIO/rpmbuild.log" 2>&1; then
        echo "    aviso: rpmbuild fallo. Log:" >&2
        tail -n 30 "$ESCENARIO/rpmbuild.log" >&2 || true
        return 1
    fi

    local rpm_generado
    rpm_generado="$(find "$topdir/RPMS" -name '*.rpm' | head -n1)"
    if [[ -z "$rpm_generado" ]]; then
        echo "    aviso: rpmbuild termino pero no se encontro ningun .rpm." >&2
        return 1
    fi

    local salida="$DIST/$(basename "$rpm_generado")"
    cp "$rpm_generado" "$salida"
    echo "    listo: $salida ($(du -h "$salida" | cut -f1))"
    return 0
}

if ! construir_rpm; then
    echo "    .rpm NO generado en esta maquina (ver aviso arriba)." >&2
    FALLOS+=(".rpm")
fi

# ------------------------------------------------------------- Referencia

echo
echo ">>> Copiando plantillas de referencia para construir en la distro destino"
mkdir -p "$DIST/referencia-empaquetado"
cp "$PKG/deb/control.in" "$DIST/referencia-empaquetado/deb-control.in"
cp "$PKG/rpm/dosalmas-launcher.spec.in" "$DIST/referencia-empaquetado/dosalmas-launcher.spec.in"

# ----------------------------------------------------------------- Limpieza

rm -rf "$ESCENARIO"

# ------------------------------------------------------------------ Resumen

echo
echo "=================================================================="
echo " Resumen"
echo "=================================================================="
ls -lh "$DIST" | grep -v '^total' || true
echo
if [[ "${#FALLOS[@]}" -eq 0 ]]; then
    echo "Todos los artefactos se generaron en esta maquina."
else
    echo "Artefactos NO generados en esta maquina: ${FALLOS[*]}"
    echo "(revisa los avisos arriba; para .deb/.rpm siempre queda la"
    echo " plantilla de referencia en dist/referencia-empaquetado/)"
fi
echo "Version empaquetada: $VERSION"
