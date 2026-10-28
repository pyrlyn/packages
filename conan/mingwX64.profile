# Windows x86_64 as K/N's mingw target links it. ConanCenter has MSVC binaries only, so these are built from the
# locked recipes (`--build=missing`) with the clang and the mingw sysroot of the K/N doing the build; the build
# script passes their paths in. CMake comes from PATH: the version below only satisfies the recipes' range.
{% set deps = os.getenv("KONAN_DEPENDENCIES") %}
{% set llvm = deps + "/" + os.getenv("KONAN_LLVM") %}
{% set sysroot = deps + "/" + os.getenv("KONAN_MINGW") %}
[settings]
os=Windows
arch=x86_64
compiler=gcc
compiler.version=9
compiler.libcxx=libstdc++11
compiler.threads=posix
compiler.exception=seh
build_type=Release

[conf]
tools.build:compiler_executables={"c": "{{ llvm }}/bin/clang", "cpp": "{{ llvm }}/bin/clang++"}
tools.build:cflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}"]
tools.build:sharedlinkflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}", "-fuse-ld=lld"]
tools.build:exelinkflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}", "-fuse-ld=lld"]
tools.build:sysroot={{ sysroot }}
tools.cmake.cmaketoolchain:system_name=Windows
tools.cmake.cmaketoolchain:system_processor=AMD64
# llvm-ar writes the symbol index itself; the host's ranlib would rewrite a COFF archive for its own format.
tools.cmake.cmaketoolchain:extra_variables={"CMAKE_RANLIB": "true"}

[buildenv]
AR={{ llvm }}/bin/llvm-ar

[platform_tool_requires]
cmake/3.31.12
