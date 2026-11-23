# Windows x86_64 as K/N's mingw target links it. ConanCenter has MSVC binaries only, so these are built from the
# locked recipes (`--build=missing`) with the clang and the mingw sysroot of the K/N doing the build; the build
# script passes their paths in. CMake comes from PATH: the version below only satisfies the recipes' range.
{% set deps = os.getenv("KONAN_DEPENDENCIES") %}
{% set llvm = deps + "/" + os.getenv("KONAN_LLVM") %}
{% set sysroot = deps + "/" + os.getenv("KONAN_MINGW") %}
{% set windows = platform.system() == "Windows" %}
{% set exe = ".exe" if windows else "" %}
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
tools.build:compiler_executables={"c": "{{ llvm }}/bin/clang{{ exe }}", "cpp": "{{ llvm }}/bin/clang++{{ exe }}"}
tools.build:cflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}"]
tools.build:sharedlinkflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}", "-fuse-ld=lld"]
tools.build:exelinkflags=["--target=x86_64-w64-mingw32", "--sysroot={{ sysroot }}", "-fuse-ld=lld"]
tools.build:sysroot={{ sysroot }}
tools.cmake.cmaketoolchain:system_name=Windows
tools.cmake.cmaketoolchain:system_processor=AMD64
# XZ_NLS: xz_utils' CMake turns native language support on when it finds the build host's gettext (Homebrew's
# libintl on a Mac), then refuses it for a mingw target; the library needs no translated messages.
{% if windows %}
# Windows has no make for CMake's default MinGW generator; Ninja comes from PATH, as CMake does.
tools.cmake.cmaketoolchain:generator=Ninja
tools.cmake.cmaketoolchain:extra_variables={"XZ_NLS": "OFF"}
{% else %}
# llvm-ar writes the symbol index itself; the host's ranlib would rewrite a COFF archive for its own format.
tools.cmake.cmaketoolchain:extra_variables={"CMAKE_RANLIB": "true", "XZ_NLS": "OFF"}
{% endif %}

[buildenv]
AR={{ llvm }}/bin/llvm-ar{{ exe }}

[platform_tool_requires]
cmake/3.31.12
