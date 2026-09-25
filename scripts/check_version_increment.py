"""Require one Android patch increment when a pull request changes code."""

import re
import subprocess
import sys
from pathlib import Path


VERSION_NAME = re.compile(r'^\s*versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"\s*$', re.MULTILINE)
VERSION_CODE = re.compile(r'^\s*versionCode\s*=\s*(\d+)\s*$', re.MULTILINE)
GRADLE_FILE = "app/build.gradle.kts"
ROOT_CODE_FILES = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties"}


def requires_bump(paths: list[str]) -> bool:
    return any(
        not path.lower().endswith(".md")
        and (
            path.startswith(("app/", "gradle/", "scripts/"))
            or path in ROOT_CODE_FILES
        )
        for path in paths
    )


def parse_versions(source: str) -> tuple[tuple[int, int, int], int]:
    names = VERSION_NAME.findall(source)
    codes = VERSION_CODE.findall(source)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("Expected exactly one numeric versionName and versionCode")
    return tuple(map(int, names[0])), int(codes[0])


def check(base_source: str, current_source: str) -> None:
    base_name, base_code = parse_versions(base_source)
    current_name, current_code = parse_versions(current_source)
    expected_name = (base_name[0], base_name[1], base_name[2] + 1)
    expected_code = base_code + 1
    if (current_name, current_code) != (expected_name, expected_code):
        raise ValueError(
            f"Expected versionName {'.'.join(map(str, expected_name))} and "
            f"versionCode {expected_code}; found "
            f"{'.'.join(map(str, current_name))} and {current_code}"
        )


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        base = 'versionCode = 1\nversionName = "1.0.0"\n'
        assert not requires_bump(["README.md", "Info/CHANGELOG.md"])
        assert requires_bump(["README.md", "app/src/main/java/Pocket.kt"])
        assert requires_bump(["app/src/test/java/PocketTest.kt"])
        assert requires_bump(["app/src/main/res/values/strings.xml"])
        assert requires_bump(["scripts/build-signed-release.ps1"])
        check(base, 'versionCode = 2\nversionName = "1.0.1"\n')
        for invalid in (
            base,
            'versionCode = 2\nversionName = "1.1.0"\n',
            'versionCode = 3\nversionName = "1.0.1"\n',
        ):
            try:
                check(base, invalid)
            except ValueError:
                continue
            raise AssertionError(f"Incorrectly accepted {invalid!r}")
        print("Version increment checks passed")
        return
    if len(sys.argv) != 2 or not re.fullmatch(r"[0-9a-fA-F]{40}", sys.argv[1]):
        raise SystemExit("Usage: check_version_increment.py <40-character base commit SHA>")
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", "-z", f"{sys.argv[1]}...HEAD"]
    )
    paths = [path.decode("utf-8") for path in changed.split(b"\0") if path]
    if not requires_bump(paths):
        print("No app or project code changed; version increment not required")
        return
    base_source = subprocess.check_output(
        ["git", "show", f"{sys.argv[1]}:{GRADLE_FILE}"], text=True
    )
    current_source = Path(GRADLE_FILE).read_text(encoding="utf-8")
    try:
        check(base_source, current_source)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    print("Android patch version and versionCode advance by one")


if __name__ == "__main__":
    main()
