#!/usr/bin/env python3
"""Build the pinned Golden Gate ARM64 library using an installed Android SDK."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def sdk_path(argument=None):
    if argument:
        return Path(argument).expanduser().resolve()
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(variable):
            return Path(os.environ[variable]).expanduser().resolve()
    raise SystemExit("Set ANDROID_HOME or pass --sdk /path/to/android-sdk.")


def build(sdk):
    dependency = ROOT / ".deps/golden-gate"
    if not (dependency / "xp").is_dir():
        raise SystemExit("Run python scripts/bootstrap.py first.")
    executable = "cmake.exe" if os.name == "nt" else "cmake"
    cmake = sdk / "cmake/3.10.2.4988404/bin" / executable
    toolchain = sdk / "ndk/23.0.7599858/build/cmake/android.toolchain.cmake"
    for path in (cmake, toolchain):
        if not path.is_file():
            raise SystemExit(f"Missing SDK component: {path}; see docs/BUILDING.md.")
    output = dependency / "xp/build/cmake/android/arm64-v8a"
    subprocess.run([
        str(cmake), "-H" + str(dependency / "xp"), "-B" + str(output),
        "-C" + str(dependency / "xp/config/profiles/android/arm64-v8a.cmake"),
        "-GNinja", "-DCMAKE_TOOLCHAIN_FILE=" + str(toolchain),
        "-DCMAKE_BUILD_TYPE=Debug", "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
    ], cwd=dependency, check=True)
    subprocess.run([
        str(cmake), "--build", str(output), "--target", "gg", "--", "-j4"
    ], cwd=dependency, check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", help="Android SDK root (defaults to ANDROID_HOME).")
    build(sdk_path(parser.parse_args().sdk))
