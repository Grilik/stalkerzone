#!/bin/bash
# run.sh <id> <level> <level.geom> : рендер-геометрия -> .szl (рельеф по-прежнему из level.cform: lv2/<id>/esc.bin)
set -e
L=$1; G=$(dirname "$(realpath "$0")"); T=$G; D=${OUT:-out}/$L
mkdir -p $D; P2=$(realpath "$2"); P3=$(realpath "$3"); cd $D
python3 $G/geo2cform.py "$P2" "$P3" ${CUT:-cut}/$L/level.cform geo.cform trees.json > geo.log; head -1 geo.log
[ -f esc.bin ] || $T/rast ${CUT:-cut}/$L/level.cform esc.bin 1.0 >/dev/null
cp ${LEVELS:-levels.json} .
$T/vox geo.cform vox.bin >/dev/null
python3 $T/export2.py ../$L.szl $L > log.txt 2>&1 || { cat log.txt; exit 1; }
rm -f vox.bin geo.cform
echo "$L $(tr '\n' ' ' < log.txt)"
