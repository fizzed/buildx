#!/bin/sh

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.." || exit 1

#
# switch to preferred shell with a login (which is critical for buildx commands to work)
#
"${SHELL}" -l << EOF
$@
EOF