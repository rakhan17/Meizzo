#!/usr/bin/env python3
"""Derive monotonic Android version metadata from MEIZZO release tags."""
from __future__ import annotations

import os
import re
import sys

TAG_PATTERN = re.compile(r"v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?")
MAX_ANDROID_VERSION_CODE = 2_100_000_000


def derive_version(tag: str) -> tuple[str, int]:
    match = TAG_PATTERN.fullmatch(tag)
    if not match:
        raise ValueError(f"Unsupported release tag: {tag}")

    major, minor, patch = map(int, match.group(1, 2, 3))
    phase, serial_text = match.group(4), match.group(5)
    if minor > 99 or patch > 99:
        raise ValueError("Minor and patch components must be between 0 and 99")

    if phase is None:
        qualifier = 999
    else:
        serial = int(serial_text)
        if not 1 <= serial <= 299:
            raise ValueError("Prerelease serial must be between 1 and 299")
        qualifier = {"alpha": 0, "beta": 300, "rc": 600}[phase] + serial

    code = major * 10_000_000 + minor * 100_000 + patch * 1_000 + qualifier
    if not 1 <= code <= MAX_ANDROID_VERSION_CODE:
        raise ValueError(f"Derived Android versionCode is out of range: {code}")
    return tag.removeprefix("v"), code


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: release_version.py <tag>")
    name, code = derive_version(sys.argv[1])
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a", encoding="utf-8") as env:
            env.write(f"MEIZZO_VERSION_NAME={name}\n")
            env.write(f"MEIZZO_VERSION_CODE={code}\n")
    print(f"Release {sys.argv[1]}: versionName={name} versionCode={code}")
