#!/usr/bin/env sh
# Downloads the OFL-licensed calligraphy fonts bundled with pelc
# from the official Google Fonts repository.
set -e

DEST="$(dirname "$0")/../feature-fonts/src/main/assets/fonts"
BASE="https://raw.githubusercontent.com/google/fonts/main/ofl"

FONT_FILES="
amiri/Amiri-Regular.ttf
amiri/Amiri-Bold.ttf
scheherazadenew/ScheherazadeNew-Regular.ttf
reemkufi/ReemKufi%5Bwght%5D.ttf
arefruqaa/ArefRuqaa-Regular.ttf
arefruqaa/ArefRuqaa-Bold.ttf
lateef/Lateef-Regular.ttf
gulzar/Gulzar-Regular.ttf
notonastaliqurdu/NotoNastaliqUrdu%5Bwght%5D.ttf
notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf
notokufiarabic/NotoKufiArabic%5Bwght%5D.ttf
harmattan/Harmattan-Regular.ttf
"

mkdir -p "$DEST"

for path in $FONT_FILES; do
    file="$(basename "$path" | sed 's/%5B/[/g; s/%5D/]/g')"
    if [ -f "$DEST/$file" ]; then
        echo "skip: $file (already exists)"
        continue
    fi
    echo "downloading: $file"
    curl -fSL -o "$DEST/$file" "$BASE/$path" || echo "WARN: failed to download $file"
done

echo
echo "Done. Files in $DEST:"
ls -la "$DEST"
