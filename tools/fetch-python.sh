#!/usr/bin/env bash
# Downloads the official Android build of CPython from python.org and lays it out for the app:
#   app/src/main/jniLibs/arm64-v8a/   libpython and the bundled C libraries (OpenSSL, SQLite, ...)
#   app/src/main/cpp/python/include/  headers for the small JNI launcher
#   app/src/main/assets/pylib.zip     the standard library (lib/pythonX.Y/...)
#   app/src/main/assets/pip.whl       the pip wheel, unpacked into the user site on first start
# Run it from the repository root. It needs curl, tar, zip, unzip and python3 (only to fetch pip).
set -euo pipefail

PY_VERSION="${PYTHON_VERSION:-3.14.10}"
PY_MINOR="${PY_VERSION%.*}"
ARCH="aarch64"
URL="https://www.python.org/ftp/python/${PY_VERSION}/python-${PY_VERSION}-${ARCH}-linux-android.tar.gz"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Downloading $URL"
curl -fL --retry 3 -o "$WORK/python.tar.gz" "$URL"
mkdir "$WORK/x"
tar -xzf "$WORK/python.tar.gz" -C "$WORK/x"

PREFIX="$(find "$WORK/x" -maxdepth 2 -type d -name prefix | head -n 1)"
if [ -z "$PREFIX" ]; then
  echo "No 'prefix' directory in the archive. Layout:" >&2
  find "$WORK/x" -maxdepth 3 | head -50 >&2
  exit 1
fi

# 1. Native libraries
JNI="$MAIN/jniLibs/arm64-v8a"
rm -rf "$JNI"; mkdir -p "$JNI"
find "$PREFIX/lib" -maxdepth 1 -type f \( -name "libpython*.so" -o -name "lib*_python.so" \) -exec cp {} "$JNI/" \;
test -f "$JNI/libpython${PY_MINOR}.so" || { echo "libpython${PY_MINOR}.so not found" >&2; ls "$PREFIX/lib" >&2; exit 1; }

# 2. Headers
INC="$MAIN/cpp/python/include"
rm -rf "$INC"; mkdir -p "$INC"
cp -r "$PREFIX/include/python${PY_MINOR}" "$INC/"

# 3. Standard library, without things a phone IDE does not need
LIBDIR="$PREFIX/lib/python${PY_MINOR}"
test -d "$LIBDIR" || { echo "$LIBDIR not found" >&2; ls "$PREFIX/lib" >&2; exit 1; }
STAGE="$WORK/stage/lib"
mkdir -p "$STAGE"
cp -r "$LIBDIR" "$STAGE/"
STD="$STAGE/python${PY_MINOR}"
rm -rf "$STD/test" "$STD/idlelib" "$STD/turtledemo" "$STD/tkinter" "$STD/lib2to3" "$STD/config-"* 2>/dev/null || true
find "$STD" -name "__pycache__" -type d -prune -exec rm -rf {} + 2>/dev/null || true
mkdir -p "$MAIN/assets"
rm -f "$MAIN/assets/pylib.zip"
( cd "$WORK/stage" && zip -qr "$MAIN/assets/pylib.zip" lib )

# 4. pip (pure Python wheel)
rm -f "$MAIN/assets/pip.whl"
python3 -m pip download pip --no-deps --only-binary=:all: -d "$WORK/pip" -q
cp "$WORK"/pip/pip-*.whl "$MAIN/assets/pip.whl"

echo "Python ${PY_VERSION} payload is ready:"
ls -la "$JNI" "$MAIN/assets"
