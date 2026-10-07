#!/usr/bin/env python3
"""Syntax check every Java source file in the repository with tree-sitter.

tree-sitter is not a compiler, but it catches unbalanced braces, broken strings and
truncated files - the failure modes that are easy to introduce when writing large
amounts of code without a local JDK.

Usage:
    python3 tools/syntax_check.py [path ...]

Exits non-zero when at least one file contains a syntax error.
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from tree_sitter import Language, Parser
    import tree_sitter_java
except ImportError:  # pragma: no cover
    print("tree_sitter / tree_sitter_java not installed; "
          "run: pip3 install --break-system-packages tree_sitter tree_sitter_java")
    raise SystemExit(2)

LANGUAGE = Language(tree_sitter_java.language())


def collect(targets: list[str]) -> list[Path]:
    files: list[Path] = []
    if not targets:
        targets = ["src"]
    for target in targets:
        path = Path(target)
        if path.is_dir():
            files.extend(sorted(path.rglob("*.java")))
        elif path.suffix == ".java" and path.exists():
            files.append(path)
    return files


def first_error(node, source: bytes):
    """Depth-first search for the first ERROR or MISSING node."""
    stack = [node]
    while stack:
        current = stack.pop()
        if current.type == "ERROR" or current.is_missing:
            return current
        stack.extend(reversed(current.children))
    return None


def main() -> int:
    files = collect(sys.argv[1:])
    if not files:
        print("no Java files found")
        return 1

    parser = Parser(LANGUAGE)
    failures = 0
    for path in files:
        source = path.read_bytes()
        tree = parser.parse(source)
        error = first_error(tree.root_node, source)
        if error is not None:
            failures += 1
            line, column = error.start_point
            snippet = source.splitlines()[line][:120].decode("utf-8", "replace")
            print(f"FAIL {path}:{line + 1}:{column + 1}: {error.type}"
                  f"\n     {snippet}")
    print(f"checked {len(files)} files, {failures} with syntax errors")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
