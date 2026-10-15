#!/usr/bin/env sh
set -eu
if [ "$#" -ne 1 ]; then printf '%s\n' 'Usage: sh build-local.sh /path/to/IDE'; exit 2; fi
project_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ide_root=$1
if [ -d "$ide_root/Contents" ]; then ide_root=$ide_root/Contents; fi
java_bin=$ide_root/jbr/bin/java
if [ -x "$ide_root/jbr/Contents/Home/bin/java" ]; then java_bin=$ide_root/jbr/Contents/Home/bin/java; fi
if [ ! -x "$java_bin" ]; then printf '%s\n' 'Use a full JDK with tools/LocalBuild.java; the selected IDE has no bundled JDK.'; exit 2; fi
exec "$java_bin" "$project_root/tools/LocalBuild.java" "$project_root" "$ide_root"
