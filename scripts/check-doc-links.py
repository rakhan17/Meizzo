#!/usr/bin/env python3
"""Check relative Markdown links and image references used by repository docs."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
files = [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md")), ROOT / "CONTRIBUTING.md",
         ROOT / "SECURITY.md", ROOT / "SUPPORT.md"]
pattern = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
errors = []
for source in files:
    if not source.exists():
        continue
    for raw in pattern.findall(source.read_text(encoding="utf-8")):
        target = raw.split("#", 1)[0].strip().strip("<>")
        if not target or "://" in target or target.startswith("mailto:"):
            continue
        candidate = (source.parent / target).resolve()
        if not candidate.exists():
            errors.append(f"{source.relative_to(ROOT)} -> {target}")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"checked {len(files)} documentation files")
