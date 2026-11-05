#!/usr/bin/env bash
# Превью площадки деревни (П-02): рельеф уровня до и после правки площадками — сверху и сбоку → PNG.
# bash tools/prefab/site.sh <l01_escape.szl> <out.png>   (без файла игры: python3 -I tools/xray/fake_szl.py /tmp/fake.szl)
set -e
cd "$(dirname "$0")/../.."
OUT=/tmp/site-cls; rm -rf $OUT; mkdir -p $OUT
javac --release 17 -encoding UTF-8 -d $OUT $(find src/main/java/ru/stalkerzone/sim src/main/java/ru/stalkerzone/zonemap -name '*.java') \
  src/main/java/ru/stalkerzone/world/ZoneBounds.java src/main/java/ru/stalkerzone/artifact/ArtifactDef.java \
  src/main/java/ru/stalkerzone/world/xray/LevelRegion.java tools/prefab/SitePreview.java
java -cp $OUT SitePreview "$1" /tmp/site.txt -60 -44 76 54
python3 -I tools/prefab/site_preview.py "$1" /tmp/site.txt "$2" -60 -44 76 54 5
