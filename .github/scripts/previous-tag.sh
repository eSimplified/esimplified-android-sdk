#!/usr/bin/env bash
set -euo pipefail

git tag --list 'v[0-9]*' '[0-9]*.[0-9]*.[0-9]*' \
    | sed -E 's/^v?([0-9].*)$/\1\t&/' \
    | sort -V -k1,1 \
    | tail -1 \
    | cut -f2
