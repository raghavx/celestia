#!/usr/bin/env bash
# Provision the Swiss Ephemeris .se1 data files for the KP engine.
#
# Downloads the planet + Moon files covering 1800-2399 (ADR-0002: supported
# full-accuracy range 1800-2100) and verifies them against scripts/ephe.sha256.
# The files are NOT committed (see .gitignore); every dev machine and the CI /
# container image run this.
#
# Usage:  scripts/fetch-ephe.sh [target-dir]
# Default target-dir: ephemeris/src/main/resources/ephe
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$REPO_ROOT/ephemeris/src/main/resources/ephe}"
MANIFEST="$REPO_ROOT/scripts/ephe.sha256"

# Swiss Ephemeris data, AGPL / covered by the Professional License (ADR-0001).
# Mirror: the aloistr/swisseph GitHub repo (astro.com's maintained mirror).
BASE_URL="https://raw.githubusercontent.com/aloistr/swisseph/master/ephe"

mkdir -p "$TARGET"
cd "$TARGET"

while read -r sum file; do
  [ -z "$file" ] && continue
  if [ -f "$file" ] && echo "$sum  $file" | shasum -a 256 -c --status 2>/dev/null; then
    echo "ok    $file (cached)"
    continue
  fi
  echo "fetch $file"
  curl -sSLf -o "$file" "$BASE_URL/$file"
done < "$MANIFEST"

echo "verify against $MANIFEST"
shasum -a 256 -c "$MANIFEST"
echo "ephemeris data ready in $TARGET"
