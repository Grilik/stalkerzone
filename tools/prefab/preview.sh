#!/usr/bin/env bash
# Превью построек по оригиналу (zonemap/build): рельеф + постройка → PNG с текстурами.
# bash tools/prefab/preview.sh <l01_escape.szl | flat:Y> <out.png> [уровень 0..3] [Класс сборщика] [разрез z=N|x=N|y=N ...]
#   flat:Y — ровная площадка на высоте Y (как даст Site), .szl не нужен.
#   Без разрезов — изометрия. С разрезами — ещё <out>-cutN.png (изометрия, срезано южнее каждой линии z=N)
#   и <out>-floorN.png (всё выше y=N снято — видно помещения), и <out>-plan.png (план сверху с рабочими местами + вертикальные разрезы по линиям).
#   labels=<файл> — подписи на плане (строки «x z текст»).
# Пример: bash tools/prefab/preview.sh flat:84 /tmp/bunker.png 1 KordonBunker z=-2 z=-8 x=-35
set -e
cd "$(dirname "$0")/../.."
SRC="$1"; PNG="$2"; LVL="${3:-2}"; CLS="${4:-KordonSouthPost}"; shift $(( $# < 4 ? $# : 4 )) || true
OUT=/tmp/prefab-cls; rm -rf $OUT; mkdir -p $OUT
javac --release 17 -encoding UTF-8 -d $OUT $(find src/main/java/ru/stalkerzone/sim src/main/java/ru/stalkerzone/zonemap -name '*.java') \
  src/main/java/ru/stalkerzone/world/ZoneBounds.java src/main/java/ru/stalkerzone/artifact/ArtifactDef.java \
  src/main/java/ru/stalkerzone/world/xray/LevelRegion.java tools/prefab/PrefabPreview.java
java -cp $OUT PrefabPreview "$SRC" /tmp/prefab.txt "$LVL" "$CLS"
python3 -I tools/iso_tex.py resourcepack/StalkerZone-Textures /tmp/prefab.txt "$PNG" 12 "$CLS, уровень $LVL"
if [ $# -gt 0 ]; then
  for c in "$@"; do case "$c" in labels=*) ;; z=*) ZC="${c#z=}"
    java -cp $OUT PrefabPreview "$SRC" /tmp/prefab-cut.txt "$LVL" "$CLS" "z<=$ZC"
    python3 -I tools/iso_tex.py resourcepack/StalkerZone-Textures /tmp/prefab-cut.txt "${PNG%.png}-cut$ZC.png" 12 "Разрез z=$ZC, ур. $LVL";;
    y=*) YC="${c#y=}"
    java -cp $OUT PrefabPreview "$SRC" /tmp/prefab-cut.txt "$LVL" "$CLS" "y<=$YC"
    python3 -I tools/iso_tex.py resourcepack/StalkerZone-Textures /tmp/prefab-cut.txt "${PNG%.png}-floor$YC.png" 22 "Срез по высоте y=$YC, ур. $LVL";;
  esac; done
  python3 -I tools/prefab/ortho.py /tmp/prefab.txt "${PNG%.png}-plan.png" "$CLS, уровень $LVL" "$@"
fi
