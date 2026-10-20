#!/usr/bin/env python3
"""Package only distributable source; never include local study data or keys."""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent.parent
output = root / "artifacts" / "student-garden-0.7.0-source.zip"
output.parent.mkdir(exist_ok=True)
allowed = [
    ".gitignore", "README.md", "LICENSE", "NOTICE", "PRIVACY.md", "CONTRIBUTING.md",
    "DESIGN.md", "branding", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", "gradle", "licenses", "tools", "fdroid", "fastlane",
    "app/build.gradle.kts", "app/proguard-rules.pro", "app/src", "docs",
]
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for name in allowed:
        source = root / name
        for file in sorted(source.rglob("*") if source.is_dir() else [source]):
            if file.is_file() and not file.is_symlink() and "__pycache__" not in file.parts:
                archive.write(file, "student-garden/" + file.relative_to(root).as_posix())
print(output)
