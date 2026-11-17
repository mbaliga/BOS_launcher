#!/bin/sh
# Stage the prototype and icon into this directory so the Clickable container can see them.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$here/www" "$here/assets"
cp "$here/../../docs/spherelauncher13.html" "$here/www/index.html"
cp "$here/../picker/assets/liftoff.svg" "$here/assets/liftoff.svg"
echo "staged www/index.html ($(wc -c < "$here/www/index.html") bytes)"
