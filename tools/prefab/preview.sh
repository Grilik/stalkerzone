#!/usr/bin/env bash
# Превью построек по оригиналу (zonemap/build/Prefabs): рельеф из .szl + постройки → PNG с текстурами.
# bash tools/prefab/preview.sh <l01_escape.szl> <out.png> [уровень 0..3]
set -e
cd "$(dirname "$0")/../.."
OUT=/tmp/prefab-cls; rm -rf $OUT; mkdir -p $OUT
javac --release 17 -encoding UTF-8 -d $OUT $(find src/main/java/ru/stalkerzone/sim src/main/java/ru/stalkerzone/zonemap -name '*.java') \
  src/main/java/ru/stalkerzone/world/ZoneBounds.java src/main/java/ru/stalkerzone/artifact/ArtifactDef.java \
  src/main/java/ru/stalkerzone/world/xray/LevelRegion.java tools/prefab/SouthPostPreview.java
java -cp $OUT SouthPostPreview "$1" /tmp/prefab.txt "${3:-2}"
python3 tools/iso_tex.py resourcepack/StalkerZone-Textures /tmp/prefab.txt "$2" 10 "Южный блокпост, уровень ${3:-2}"
