# Matches ConanCenter's prebuilt macOS arm64 binaries; `--build=never` keeps it to those.
[settings]
os=Macos
arch=armv8
compiler=apple-clang
compiler.version=17
compiler.libcxx=libc++
build_type=Release
