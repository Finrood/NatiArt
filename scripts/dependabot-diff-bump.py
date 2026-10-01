#!/usr/bin/env python3
"""Classify one dependency version change from the actual PR patch."""

import re
import sys

VERSION = re.compile(r"(?<![A-Za-z0-9])v?([0-9]+)\.([0-9]+)\.([0-9]+)(?![A-Za-z0-9.-])")
MANIFEST = re.compile(r"^(?:frontend/natiart-app/package\.json|package\.json|backend/[^/]+/build\.gradle(?:\.kts)?|gradle/libs\.versions\.toml)$")
LOCKFILE = re.compile(r"^(?:frontend/natiart-app/package-lock\.json|package-lock\.json|backend/[^/]+/gradle\.lockfile|gradle/verification-metadata\.xml)$")


def manifest_line(path: str, line: str) -> bool:
    if path.endswith("package.json"):
        return bool(re.fullmatch(r'\s*"[^\"]+"\s*:\s*"[~^]?v?[0-9]+\.[0-9]+\.[0-9]+"\s*,?\s*', line))
    if path.endswith(".toml"):
        return bool(re.fullmatch(r'\s*[A-Za-z0-9_.-]+\s*=\s*"v?[0-9]+\.[0-9]+\.[0-9]+"\s*', line))
    return bool(re.match(r"\s*(?:implementation|api|runtimeOnly|testImplementation|id)\s*\(", line)) or bool(
        re.match(r"\s*[A-Za-z0-9_.-]+\s*=", line)
    )


def classify(patch: str) -> str:
    changed: dict[str, tuple[list[str], list[str]]] = {}
    path = ""
    for line in patch.splitlines():
        if line.startswith("diff --git a/"):
            parts = line.split(" b/", 1)
            path = parts[1] if len(parts) == 2 else ""
            if not MANIFEST.fullmatch(path) and not LOCKFILE.fullmatch(path):
                return "unknown"
            if MANIFEST.fullmatch(path):
                changed.setdefault(path, ([], []))
        elif MANIFEST.fullmatch(path) and line.startswith("-") and not line.startswith("---"):
            changed[path][0].append(line[1:])
        elif MANIFEST.fullmatch(path) and line.startswith("+") and not line.startswith("+++"):
            changed[path][1].append(line[1:])

    if len(changed) != 1:
        return "unknown"
    path, (removed, added) = next(iter(changed.items()))
    if len(removed) != 1 or len(added) != 1:
        return "unknown"
    old_line, new_line = removed[0], added[0]
    if not manifest_line(path, old_line) or not manifest_line(path, new_line):
        return "unknown"
    old_versions, new_versions = list(VERSION.finditer(old_line)), list(VERSION.finditer(new_line))
    if len(old_versions) != 1 or len(new_versions) != 1:
        return "unknown"
    old, new = old_versions[0], new_versions[0]
    if old_line[: old.start()] + "<VERSION>" + old_line[old.end() :] != (
        new_line[: new.start()] + "<VERSION>" + new_line[new.end() :]
    ):
        return "unknown"
    try:
        old_parts = tuple(int(part) for part in old.groups())
        new_parts = tuple(int(part) for part in new.groups())
    except ValueError:
        return "unknown"
    if new_parts <= old_parts or any(len(part) > 9 for part in old.groups() + new.groups()):
        return "unknown"
    if new_parts[0] != old_parts[0]:
        return "major"
    if new_parts[1] != old_parts[1]:
        return "minor"
    return "patch"


if __name__ == "__main__":
    print(classify(sys.stdin.read()))
