#!/usr/bin/env sh
# Downloads HarfBuzz sources used by the pelc shaping engine, so that the
# Gradle/CMake build can run fully offline afterwards.
set -e

HB_VERSION="8.5.0"
DEST="$(dirname "$0")/../core-shaping/src/main/cpp/third_party"
TARBALL="$DEST/harfbuzz-$HB_VERSION.tar.xz"

mkdir -p "$DEST"
if [ -f "$DEST/harfbuzz-$HB_VERSION/src/harfbuzz.cc" ]; then
    echo "HarfBuzz $HB_VERSION already present."
    exit 0
fi

echo "Downloading harfbuzz-$HB_VERSION.tar.xz ..."
curl -fSL -o "$TARBALL" "https://github.com/harfbuzz/harfbuzz/releases/download/$HB_VERSION/harfbuzz-$HB_VERSION.tar.xz"
tar -xf "$TARBALL" -C "$DEST"
rm -f "$TARBALL"
echo "Done: $DEST/harfbuzz-$HB_VERSION"
