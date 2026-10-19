#!/usr/bin/env python3
"""Fetch the exact public transport sources recorded in dependencies.lock.json."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEPENDENCY = ROOT / ".deps" / "golden-gate"


def run(*arguments, cwd=None):
    subprocess.run(arguments, cwd=cwd, check=True)


def revision(path):
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=path, text=True
    ).strip()


def main():
    lock = json.loads((ROOT / "dependencies.lock.json").read_text())["golden_gate"]
    if not (DEPENDENCY / ".git").exists():
        DEPENDENCY.mkdir(parents=True, exist_ok=True)
        run("git", "init", "--quiet", str(DEPENDENCY))
        run("git", "remote", "add", "origin", lock["url"], cwd=DEPENDENCY)
        run("git", "fetch", "--depth", "1", "origin", lock["commit"], cwd=DEPENDENCY)
        run("git", "checkout", "--detach", lock["commit"], cwd=DEPENDENCY)
    if revision(DEPENDENCY) != lock["commit"]:
        raise SystemExit("Transport revision differs from the lock file; use a clean .deps checkout.")
    run(
        "git", "submodule", "update", "--init", "--depth", "1",
        *lock["submodules"], cwd=DEPENDENCY
    )
    for path, expected in lock["submodules"].items():
        if revision(DEPENDENCY / path) != expected:
            raise SystemExit(f"Dependency revision mismatch: {path}")

    # The Android JNI bridge has a separate link step from the Golden Gate C core.
    # Build both shared libraries with 16 KiB page alignment for newer Android phones.
    cmake = DEPENDENCY / "platform/android/goldengate/GoldenGateBindings/CMakeLists.txt"
    text = cmake.read_text(encoding="utf-8")
    marker = "# Air Remote: Android 16 KiB page alignment."
    if marker not in text:
        cmake.write_text(
            text + "\n" + marker +
            '\nset_property(TARGET xp APPEND_STRING PROPERTY LINK_FLAGS " -Wl,-z,max-page-size=16384")\n',
            encoding="utf-8"
        )
    print("Pinned transport sources are ready in .deps/golden-gate.")


if __name__ == "__main__":
    main()
