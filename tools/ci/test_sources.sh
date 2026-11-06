#!/usr/bin/env bash
# Список исходников для тестов симуляции (чистый javac, без Minecraft) — один на CI (build.yml) и конвейер (check.sh).
# Новый чистый класс, нужный тестам, добавлять только сюда.
cd "$(dirname "$0")/../.."
find src/main/java/ru/stalkerzone/sim src/main/java/ru/stalkerzone/zonemap -name '*.java' | sort
echo src/main/java/ru/stalkerzone/world/ZoneBounds.java
echo src/main/java/ru/stalkerzone/artifact/ArtifactDef.java
echo src/main/java/ru/stalkerzone/gear/TaczWeight.java
ls src/test/java/*.java
