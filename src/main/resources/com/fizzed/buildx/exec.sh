#!/bin/sh -l

BASEDIR=$(dirname "$0")
cd "$BASEDIR/.." || exit 1

"$@"