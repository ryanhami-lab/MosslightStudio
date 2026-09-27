#!/usr/bin/env python3
"""Audit a public Android candidate without embedding private-source identifiers.

The optional denylist is a UTF-8 text file stored outside the candidate tree. Each
nonblank, non-comment line is matched case-insensitively against candidate text,
relative paths, APK entry names, and UTF-8/UTF-16 APK entry bytes. Contiguous
raw/printable encoded runs are checked for bounded local-home and Windows-absolute
path patterns without lossy decoding across unrelated binary bytes. Reports
identify a token by a short digest and report only APK entry/marker categories,
never matched values.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile
import xml.etree.ElementTree as ET


IGNORED_DIRECTORIES = {".git", ".gradle", ".kotlin", "build"}
TEXT_SUFFIXES = {
    ".gradle",
    ".java",
    ".json",
    ".kts",
    ".kt",
    ".md",
    ".properties",
    ".pro",
    ".py",
    ".toml",
    ".txt",
    ".xml",
    ".yml",
    ".yaml",
}
TEXT_NAMES = {"gradlew", "gradlew.bat", ".gitignore", ".gitattributes", "LICENSE"}
SENSITIVE_NAMES = {"local.properties", "credentials.json", "secrets.json"}
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".key", ".p12", ".pfx", ".pem"}
WINDOWS_ABSOLUTE_PATH = re.compile(r"(?<![A-Za-z0-9_])[A-Za-z]:[\\/]")
UNIX_HOME_PATH = re.compile(r"(?<![A-Za-z0-9_])/(?:Users|home)/[^/\s]+/")
APK_WINDOWS_ABSOLUTE_PATH = re.compile(
    r"(?i)(?<![A-Za-z0-9_])[A-Z]:[\\/]"
    r"[^\\/\x00\r\n\t:<>\"|?*]{1,80}[\\/]"
    r"[^\\/\x00\r\n\t:<>\"|?*]{1,120}"
)
APK_LOCAL_HOME_PATH = re.compile(
    r"(?i)(?<![A-Za-z0-9_])(?:[A-Z]:[\\/](?:Users|Documents and Settings)[\\/]"
    r"[^\\/\x00\s]{1,80}[\\/]|/(?:Users|home)/[^/\x00\s]{1,80}/)"
)
APK_WINDOWS_ABSOLUTE_PATH_BYTES = re.compile(
    rb"(?i)(?<![A-Za-z0-9_])[A-Z]:[\\/]"
    rb"[^\\/\x00\r\n\t:<>\"|?*]{1,80}[\\/]"
    rb"[^\\/\x00\r\n\t:<>\"|?*]{1,120}"
)
APK_LOCAL_HOME_PATH_BYTES = re.compile(
    rb"(?i)(?<![A-Za-z0-9_])(?:[A-Z]:[\\/](?:Users|Documents and Settings)[\\/]"
    rb"[^\\/\x00\s]{1,80}[\\/]|/(?:Users|home)/[^/\x00\s]{1,80}/)"
)
UTF16LE_PRINTABLE_RUN = re.compile(rb"(?:[\x20-\x7e]\x00){6,}")
UTF16BE_PRINTABLE_RUN = re.compile(rb"(?:\x00[\x20-\x7e]){6,}")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
TOOLS_NS = "{http://schemas.android.com/tools}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit candidate source and optional APKs.")
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Candidate root; defaults to the parent of this tools directory.",
    )
    parser.add_argument(
        "--apk",
        type=Path,
        action="append",
        default=[],
        help="APK to inventory and scan. May be supplied more than once.",
    )
    parser.add_argument(
        "--private-denylist",
        type=Path,
        help="External UTF-8 denylist. It must be outside the candidate root.",
    )
    parser.add_argument(
        "--require-private-denylist",
        action="store_true",
        help="Fail when no external denylist is supplied.",
    )
    parser.add_argument(
        "--require-apk",
        action="store_true",
        help="Fail when no APK is supplied.",
    )
    parser.add_argument("--output", type=Path, help="Write JSON here instead of stdout.")
    return parser.parse_args()


def resolved(path: Path) -> Path:
    return path.expanduser().resolve()


def is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def token_id(token: str) -> str:
    return sha256_bytes(token.casefold().encode("utf-8"))[:12]


def load_denylist(path: Path | None, project_root: Path) -> list[tuple[str, tuple[bytes, ...]]]:
    if path is None:
        return []
    denylist = resolved(path)
    if is_within(denylist, project_root):
        raise ValueError("The private denylist must remain outside the candidate root")
    lines = denylist.read_text(encoding="utf-8").splitlines()
    tokens: dict[str, tuple[bytes, ...]] = {}
    for line in lines:
        token = line.strip()
        if not token or token.startswith("#"):
            continue
        if len(token) < 3:
            raise ValueError("Every denylist token must contain at least three characters")
        folded = token.casefold()
        variants = tuple(
            dict.fromkeys(
                (
                    folded.encode("utf-8"),
                    folded.encode("utf-16-le"),
                    folded.encode("utf-16-be"),
                )
            )
        )
        tokens.setdefault(token_id(token), variants)
    return sorted(tokens.items())


def candidate_files(project_root: Path) -> list[Path]:
    result: list[Path] = []
    for path in project_root.rglob("*"):
        relative = path.relative_to(project_root)
        if any(part in IGNORED_DIRECTORIES for part in relative.parts):
            continue
        if path.is_file() or path.is_symlink():
            result.append(path)
    return sorted(result, key=lambda item: item.relative_to(project_root).as_posix())


def source_category(path: Path) -> str:
    suffix = path.suffix.casefold()
    if suffix in {".kt", ".java"}:
        return "source"
    if suffix in {".xml"}:
        return "resource_xml"
    if suffix in {".gradle", ".kts", ".toml", ".properties"}:
        return "build_configuration"
    if suffix in {".md", ".txt"}:
        return "documentation"
    if suffix in {".png", ".webp", ".jpg", ".jpeg", ".svg"}:
        return "image"
    if suffix in {".wav", ".ogg", ".mp3", ".m4a"}:
        return "audio"
    if suffix in {".jar"}:
        return "binary_dependency"
    return "other"


def is_text_candidate(path: Path) -> bool:
    return path.suffix.casefold() in TEXT_SUFFIXES or path.name in TEXT_NAMES


def scan_text(
    path: Path,
    relative: str,
    deny_tokens: list[tuple[str, tuple[bytes, ...]]],
    findings: list[dict[str, object]],
) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        findings.append({"severity": "error", "code": "invalid_utf8", "path": relative})
        return

    folded_path = relative.casefold().encode("utf-8")
    for identifier, variants in deny_tokens:
        needle = variants[0]
        if needle in folded_path:
            findings.append(
                {"severity": "error", "code": "denylist_path_match", "path": relative, "tokenId": identifier}
            )

    for line_number, line in enumerate(text.splitlines(), start=1):
        folded = line.casefold().encode("utf-8")
        for identifier, variants in deny_tokens:
            needle = variants[0]
            if needle in folded:
                findings.append(
                    {
                        "severity": "error",
                        "code": "denylist_text_match",
                        "path": relative,
                        "line": line_number,
                        "tokenId": identifier,
                    }
                )
        if WINDOWS_ABSOLUTE_PATH.search(line) or UNIX_HOME_PATH.search(line):
            findings.append(
                {"severity": "error", "code": "absolute_local_path", "path": relative, "line": line_number}
            )


def source_inventory(
    project_root: Path,
    deny_tokens: list[tuple[str, tuple[bytes, ...]]],
    findings: list[dict[str, object]],
) -> dict[str, object]:
    files = candidate_files(project_root)
    categories: Counter[str] = Counter()
    suffixes: Counter[str] = Counter()
    total_bytes = 0
    tree_digest = hashlib.sha256()

    for path in files:
        relative = path.relative_to(project_root).as_posix()
        if path.is_symlink():
            findings.append({"severity": "error", "code": "symlink", "path": relative})
            continue
        size = path.stat().st_size
        digest = sha256_file(path)
        categories[source_category(path)] += 1
        suffixes[path.suffix.casefold() or "<none>"] += 1
        total_bytes += size
        tree_digest.update(relative.encode("utf-8"))
        tree_digest.update(b"\0")
        tree_digest.update(digest.encode("ascii"))
        tree_digest.update(b"\n")

        lower_name = path.name.casefold()
        if lower_name in SENSITIVE_NAMES or path.suffix.casefold() in SENSITIVE_SUFFIXES:
            findings.append({"severity": "error", "code": "sensitive_file", "path": relative})
        if is_text_candidate(path):
            scan_text(path, relative, deny_tokens, findings)
        elif deny_tokens:
            folded_path = relative.casefold().encode("utf-8")
            for identifier, variants in deny_tokens:
                needle = variants[0]
                if needle in folded_path:
                    findings.append(
                        {
                            "severity": "error",
                            "code": "denylist_path_match",
                            "path": relative,
                            "tokenId": identifier,
                        }
                    )

    return {
        "fileCount": len(files),
        "totalBytes": total_bytes,
        "treeSha256": tree_digest.hexdigest(),
        "categories": dict(sorted(categories.items())),
        "suffixes": dict(sorted(suffixes.items())),
    }


def gradle_metadata(project_root: Path) -> dict[str, object]:
    build_file = project_root / "app" / "build.gradle.kts"
    if not build_file.is_file():
        return {"present": False}
    text = build_file.read_text(encoding="utf-8")

    def value(name: str) -> str | int | None:
        match = re.search(rf"(?m)^\s*{re.escape(name)}\s*=\s*(?:\"([^\"]+)\"|(\d+))", text)
        if not match:
            return None
        return match.group(1) if match.group(1) is not None else int(match.group(2))

    dependency_lines = re.findall(
        r"(?m)^\s*(?:implementation|debugImplementation|testImplementation|androidTestImplementation)\s*\(",
        text,
    )
    return {
        "present": True,
        "namespace": value("namespace"),
        "applicationId": value("applicationId"),
        "compileSdk": value("compileSdk"),
        "minSdk": value("minSdk"),
        "targetSdk": value("targetSdk"),
        "versionCode": value("versionCode"),
        "versionName": value("versionName"),
        "declaredDependencyLineCount": len(dependency_lines),
    }


def manifest_metadata(project_root: Path, findings: list[dict[str, object]]) -> dict[str, object]:
    manifest = project_root / "app" / "src" / "main" / "AndroidManifest.xml"
    if not manifest.is_file():
        return {"present": False}
    try:
        root = ET.parse(manifest).getroot()
    except ET.ParseError:
        findings.append(
            {"severity": "error", "code": "manifest_parse_error", "path": manifest.relative_to(project_root).as_posix()}
        )
        return {"present": True, "parsed": False}

    application = root.find("application")
    component_counts: Counter[str] = Counter()
    exported_counts: Counter[str] = Counter()
    if application is not None:
        for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
            for component in application.findall(kind):
                if component.get(TOOLS_NS + "node") == "remove":
                    continue
                component_counts[kind] += 1
                if component.get(ANDROID_NS + "exported") == "true":
                    exported_counts[kind] += 1
    permissions = sorted(
        item.get(ANDROID_NS + "name", "") for item in root.findall("uses-permission")
    )
    return {
        "present": True,
        "parsed": True,
        "permissionNames": permissions,
        "componentCounts": dict(sorted(component_counts.items())),
        "exportedComponentCounts": dict(sorted(exported_counts.items())),
        "allowBackup": application.get(ANDROID_NS + "allowBackup") if application is not None else None,
    }


def apk_entry_category(name: str) -> str:
    upper = name.upper()
    if re.fullmatch(r"classes\d*\.dex", name):
        return "dex"
    if name == "AndroidManifest.xml":
        return "manifest"
    if name == "resources.arsc" or name.startswith("res/"):
        return "resources"
    if name.startswith("assets/"):
        return "assets"
    if name.startswith("lib/"):
        return "native_libraries"
    if name.startswith("META-INF/") and upper.endswith((".RSA", ".DSA", ".EC", ".SF", ".MF")):
        return "signing_metadata"
    if name.startswith("META-INF/") and any(part in upper for part in ("LICENSE", "NOTICE", "DEPENDENCIES")):
        return "license_metadata"
    if name.startswith("kotlin/") or name.startswith("META-INF/"):
        return "runtime_metadata"
    return "other"


def stream_findings(
    stream: object,
    deny_tokens: list[tuple[str, tuple[bytes, ...]]],
) -> tuple[set[str], set[str]]:
    maximum = max(
        (len(variant) for _, variants in deny_tokens for variant in variants),
        default=0,
    )
    overlap = max(maximum - 1, 512)
    tail = b""
    found: set[str] = set()
    path_markers: set[str] = set()
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        window = tail + chunk
        folded = window.lower()
        for identifier, variants in deny_tokens:
            if identifier not in found and any(variant in folded for variant in variants):
                found.add(identifier)
        if APK_WINDOWS_ABSOLUTE_PATH_BYTES.search(window):
            path_markers.add("windows_absolute")
        if APK_LOCAL_HOME_PATH_BYTES.search(window):
            path_markers.add("local_home")
        encoded_runs = (
            (UTF16LE_PRINTABLE_RUN, "utf-16-le"),
            (UTF16BE_PRINTABLE_RUN, "utf-16-be"),
        )
        for run_pattern, encoding in encoded_runs:
            decoded = " ".join(
                match.group(0).decode(encoding, errors="strict")
                for match in run_pattern.finditer(window)
            )
            if APK_WINDOWS_ABSOLUTE_PATH.search(decoded):
                path_markers.add("windows_absolute")
            if APK_LOCAL_HOME_PATH.search(decoded):
                path_markers.add("local_home")
        tail = window[-overlap:]
    return found, path_markers


def apk_inventory(
    apk: Path,
    deny_tokens: list[tuple[str, tuple[bytes, ...]]],
    findings: list[dict[str, object]],
) -> dict[str, object]:
    apk = resolved(apk)
    label = apk.name
    if not apk.is_file():
        findings.append({"severity": "error", "code": "apk_missing", "path": label})
        return {"name": label, "present": False}
    if not zipfile.is_zipfile(apk):
        findings.append({"severity": "error", "code": "apk_not_zip", "path": label})
        return {"name": label, "present": True, "zip": False, "sha256": sha256_file(apk)}

    categories: Counter[str] = Counter()
    uncompressed: Counter[str] = Counter()
    reported_path_markers: set[tuple[str, str]] = set()
    with zipfile.ZipFile(apk) as archive:
        entries = sorted(archive.infolist(), key=lambda item: item.filename)
        for entry in entries:
            category = apk_entry_category(entry.filename)
            categories[category] += 1
            uncompressed[category] += entry.file_size
            folded_name = entry.filename.casefold().encode("utf-8")
            for identifier, variants in deny_tokens:
                token = variants[0]
                if token in folded_name:
                    findings.append(
                        {
                            "severity": "error",
                            "code": "denylist_apk_entry_name_match",
                            "path": label,
                            "entryCategory": category,
                            "tokenId": identifier,
                        }
                    )
            if entry.is_dir():
                continue
            with archive.open(entry) as stream:
                token_matches, path_markers = stream_findings(stream, deny_tokens)
                for identifier in token_matches:
                    findings.append(
                        {
                            "severity": "error",
                            "code": "denylist_apk_content_match",
                            "path": label,
                            "entryCategory": category,
                            "tokenId": identifier,
                        }
                    )
                for marker in path_markers:
                    marker_key = (category, marker)
                    if marker_key in reported_path_markers:
                        continue
                    reported_path_markers.add(marker_key)
                    findings.append(
                        {
                            "severity": "error",
                            "code": "apk_local_path_marker",
                            "path": label,
                            "entryCategory": category,
                            "markerCategory": marker,
                        }
                    )

    return {
        "name": label,
        "present": True,
        "zip": True,
        "sizeBytes": apk.stat().st_size,
        "sha256": sha256_file(apk),
        "entryCount": sum(categories.values()),
        "entryCategories": dict(sorted(categories.items())),
        "uncompressedBytesByCategory": dict(sorted(uncompressed.items())),
    }


def main() -> int:
    args = parse_args()
    project_root = resolved(args.project_root)
    if not project_root.is_dir():
        raise SystemExit("Project root does not exist or is not a directory")

    findings: list[dict[str, object]] = []
    try:
        deny_tokens = load_denylist(args.private_denylist, project_root)
    except (OSError, UnicodeError, ValueError) as error:
        raise SystemExit(str(error)) from error

    if args.require_private_denylist and not deny_tokens:
        findings.append({"severity": "error", "code": "private_denylist_required"})
    if args.require_apk and not args.apk:
        findings.append({"severity": "error", "code": "apk_required"})

    source = source_inventory(project_root, deny_tokens, findings)
    source["gradle"] = gradle_metadata(project_root)
    source["manifest"] = manifest_metadata(project_root, findings)
    source["thirdPartyNoticesPresent"] = (project_root / "docs" / "THIRD_PARTY_NOTICES.md").is_file()
    apks = [apk_inventory(item, deny_tokens, findings) for item in args.apk]
    findings.sort(
        key=lambda item: (
            str(item.get("severity", "")),
            str(item.get("code", "")),
            str(item.get("path", "")),
            int(item.get("line", 0)),
            str(item.get("tokenId", "")),
        )
    )
    failed = any(item.get("severity") == "error" for item in findings)
    report = {
        "schemaVersion": 1,
        "status": "fail" if failed else "pass",
        "projectName": project_root.name,
        "denylist": {
            "provided": args.private_denylist is not None,
            "externalToProject": args.private_denylist is None
            or not is_within(resolved(args.private_denylist), project_root),
            "tokenCount": len(deny_tokens),
        },
        "source": source,
        "apks": apks,
        "findings": findings,
    }
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        output = resolved(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(encoded, encoding="utf-8")
    else:
        sys.stdout.write(encoded)
    return 2 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
