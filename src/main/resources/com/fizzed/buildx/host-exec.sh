#!/bin/sh

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.." || exit 1

#
# switch to preferred shell with a login (which is critical for buildx commands to work)
#
SHELL_BIN="${SHELL}"

if [ -z "$SHELL_BIN" ]; then
  echo "No default shell set, will use /bin/sh by default." >&2
  SHELL_BIN="/bin/sh"
fi

"${SHELL_BIN}" -l << EOF
$@
EOF