#!/bin/sh
# bad hack to rename the final apk
mkdir -p outputs
cmd="cp android/build/outputs/apk/MozStumbler-$1.apk outputs/MozStumbler-`git describe --abbrev=0 --tags`.apk"
`${cmd}`
