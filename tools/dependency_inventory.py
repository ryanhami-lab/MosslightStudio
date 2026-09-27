#!/usr/bin/env python3
"""Normalize a resolved Gradle dependency report into reviewable JSON.

Generate the input with the candidate's own wrapper, for example by requesting the
release runtime classpath. This parser does not invoke Gradle or infer licenses.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


DEPENDENCY_LINE = re.compile(
    r"^[|+\\\s]*---\s+"
    r"(?P<group>[A-Za-z0-9_.-]+):(?P<name>[A-Za-z0-9_.-]+):(?P<requested>\S+)"
    r"(?:\s+->\s+(?P<resolved>[^\s(*)]+))?"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Normalize Gradle dependency output.")
    parser.add_argument("--input", default="-", help="Report path, or - for stdin.")
    parser.add_argument("--configuration", required=True, help="Resolved Gradle configuration name.")
    parser.add_argument("--output", type=Path, help="Write JSON here instead of stdout.")
    return parser.parse_args()


def read_input(value: str) -> tuple[str, str]:
    if value == "-":
        text = sys.stdin.read()
        return text, "stdin"
    path = Path(value).expanduser().resolve()
    return path.read_text(encoding="utf-8"), path.name


def clean_version(requested: str, resolved: str | None) -> str | None:
    chosen = resolved or requested
    chosen = chosen.strip()
    if chosen.startswith("{") or chosen in {"FAILED", "project"}:
        return None
    return chosen


def main() -> int:
    args = parse_args()
    text, source_name = read_input(args.input)
    coordinates: set[str] = set()
    failed_lines = 0
    constraint_lines = 0

    for line in text.splitlines():
        if " FAILED" in line:
            failed_lines += 1
            continue
        match = DEPENDENCY_LINE.match(line)
        if not match:
            continue
        if " (c)" in line or " (n)" in line:
            constraint_lines += 1
            continue
        version = clean_version(match.group("requested"), match.group("resolved"))
        if version is None:
            continue
        coordinates.add(f"{match.group('group')}:{match.group('name')}:{version}")

    report = {
        "schemaVersion": 1,
        "configuration": args.configuration,
        "sourceName": source_name,
        "sourceSha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        "coordinateCount": len(coordinates),
        "coordinates": sorted(coordinates),
        "constraintLinesSkipped": constraint_lines,
        "failedDependencyLines": failed_lines,
        "licenseStatus": "not_evaluated",
    }
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        output = args.output.expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(encoded, encoding="utf-8")
    else:
        sys.stdout.write(encoded)
    return 2 if failed_lines else 0


if __name__ == "__main__":
    raise SystemExit(main())
