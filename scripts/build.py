#!/usr/bin/env python3
"""Build dependencies, run app unit tests, and assemble a debug APK."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

from build_native import sdk_path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", help="Android SDK root (defaults to ANDROID_HOME).")
    args = parser.parse_args()
    sdk = sdk_path(args.sdk)
    environment = os.environ.copy()
    environment["ANDROID_HOME"] = str(sdk)
    # Older Android tooling also reads this variable; avoid conflicting installed SDKs.
    environment["ANDROID_SDK_ROOT"] = str(sdk)
    # Legacy AGP can cache SDK discovery in its daemon. A project-local SDK setting
    # is unambiguous and is intentionally excluded from source control.
    local_properties = ROOT / "local.properties"
    properties = local_properties.read_text(encoding="utf-8") if local_properties.exists() else ""
    for name, value in (
        ("sdk.dir", sdk),
        ("ndk.dir", sdk / "ndk/23.0.7599858"),
    ):
        setting = name + "=" + value.as_posix()
        pattern = r"(?m)^" + re.escape(name) + r"\s*=.*$"
        if re.search(pattern, properties):
            properties = re.sub(pattern, lambda _: setting, properties)
        else:
            properties += "\n" + setting + "\n"
    local_properties.write_text(properties, encoding="utf-8")
    subprocess.run([sys.executable, str(ROOT / "scripts/bootstrap.py")], check=True)
    subprocess.run([
        sys.executable, str(ROOT / "scripts/build_native.py"), "--sdk", str(sdk)
    ], check=True)
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if os.name != "nt":
        wrapper.chmod(wrapper.stat().st_mode | 0o111)
    subprocess.run([
        str(wrapper), ":app:testDebugUnitTest", ":app:assembleDebug", "--console=plain"
    ], cwd=ROOT, env=environment, check=True)
    print("APK: app/build/outputs/apk/debug/app-debug.apk")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.returncode) from None
