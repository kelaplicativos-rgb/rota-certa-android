#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import stat
import subprocess
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

TEXT_EXTENSIONS = {
    ".kt", ".kts", ".java", ".xml", ".yml", ".yaml", ".json", ".md", ".txt",
    ".py", ".sh", ".properties", ".gradle", ".toml", ".pro", ".cfg", ".ini",
    ".csv", ".tsv", ".html", ".css", ".js", ".ts", ".bat", ".ps1",
}
SENSITIVE_PATTERNS = (
    "local.properties", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key",
    "debug-signing/", "secrets/", ".env", "credentials", "google-services.json",
)

RISK_PATTERNS = [
    ("CRITICAL", "build_mutates_source", re.compile(r"\b(?:writeText|writeBytes|appendText)\s*\("), "Build/script writes source or repository files."),
    ("CRITICAL", "hardcoded_private_key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Private key material appears in text."),
    ("HIGH", "hardcoded_secret_name", re.compile(r"(?i)\b(?:api[_-]?key|secret|token|password)\b\s*[:=]\s*[\"'][^\"']{8,}[\"']"), "Possible hardcoded credential."),
    ("HIGH", "non_null_assertion", re.compile(r"!!"), "Kotlin non-null assertion can crash at runtime."),
    ("HIGH", "global_scope", re.compile(r"\bGlobalScope\b"), "Unstructured coroutine scope."),
    ("HIGH", "run_blocking", re.compile(r"\brunBlocking\s*\("), "Blocking coroutine bridge may freeze a thread."),
    ("HIGH", "thread_sleep", re.compile(r"\bThread\.sleep\s*\("), "Explicit thread blocking."),
    ("HIGH", "infinite_loop", re.compile(r"\bwhile\s*\(\s*true\s*\)"), "Potential infinite loop."),
    ("HIGH", "exported_component", re.compile(r"android:exported\s*=\s*[\"']true[\"']"), "Exported Android component requires review."),
    ("MEDIUM", "broad_exception", re.compile(r"catch\s*\(\s*\w+\s*:\s*(?:Exception|Throwable)\s*\)"), "Broad exception handling."),
    ("MEDIUM", "empty_catch", re.compile(r"catch\s*\([^)]*\)\s*\{\s*\}"), "Empty catch block hides failures."),
    ("MEDIUM", "todo_marker", re.compile(r"(?i)\b(?:TODO|FIXME|HACK|XXX)\b"), "Unresolved maintenance marker."),
    ("MEDIUM", "force_unwrap_get", re.compile(r"\.get\(\)"), "Unchecked Optional/Result-style get requires review."),
    ("LOW", "system_print", re.compile(r"\b(?:println|System\.out\.print(?:ln)?)\s*\("), "Direct console output in source."),
]

SYMBOL_PATTERNS = {
    "class": re.compile(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)"),
    "data_class": re.compile(r"\bdata\s+class\s+([A-Za-z_][A-Za-z0-9_]*)"),
    "object": re.compile(r"\bobject\s+([A-Za-z_][A-Za-z0-9_]*)"),
    "interface": re.compile(r"\binterface\s+([A-Za-z_][A-Za-z0-9_]*)"),
    "enum": re.compile(r"\benum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)"),
    "function": re.compile(r"\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*\("),
}

@dataclass
class FileRecord:
    path: str
    tracked: bool
    size_bytes: int
    sha256: str
    extension: str
    file_mode: str
    binary: bool
    sensitive: bool
    line_count: int
    nonblank_lines: int
    character_count: int
    max_line_length: int
    trailing_whitespace_lines: int
    tab_lines: int
    has_final_newline: bool
    open_braces: int
    close_braces: int
    open_parentheses: int
    close_parentheses: int
    open_brackets: int
    close_brackets: int
    symbol_count: int
    risk_count: int


def run(*args: str) -> str:
    completed = subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return completed.stdout.decode("utf-8", errors="replace")


def git_paths(include_untracked: bool) -> list[tuple[str, bool]]:
    tracked_raw = subprocess.check_output(["git", "ls-files", "-z"])
    result = [(item.decode("utf-8", errors="surrogateescape"), True) for item in tracked_raw.split(b"\0") if item]
    if include_untracked:
        untracked_raw = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z"])
        result.extend((item.decode("utf-8", errors="surrogateescape"), False) for item in untracked_raw.split(b"\0") if item)
    return sorted(set(result), key=lambda item: item[0])


def is_sensitive(path: str) -> bool:
    lower = path.lower()
    return any(pattern in lower for pattern in SENSITIVE_PATTERNS)


def is_binary_bytes(data: bytes, path: Path) -> bool:
    if path.suffix.lower() in TEXT_EXTENSIONS:
        return False
    sample = data[:8192]
    if b"\0" in sample:
        return True
    if not sample:
        return False
    printable = sum(byte in b"\n\r\t\f\b" or 32 <= byte <= 126 or byte >= 128 for byte in sample)
    return printable / len(sample) < 0.75


def line_numbered(text: str) -> str:
    lines = text.splitlines()
    if not lines and text:
        lines = [text]
    width = max(1, len(str(len(lines))))
    return "\n".join(f"{index:0{width}d}: {line}" for index, line in enumerate(lines, 1))


def detect_symbols(text: str) -> dict[str, list[str]]:
    return {kind: sorted(set(pattern.findall(text))) for kind, pattern in SYMBOL_PATTERNS.items()}


def detect_risks(path: str, text: str) -> list[dict[str, object]]:
    findings: list[dict[str, object]] = []
    for line_number, line in enumerate(text.splitlines(), 1):
        for severity, code, pattern, message in RISK_PATTERNS:
            if pattern.search(line):
                findings.append({
                    "severity": severity,
                    "code": code,
                    "path": path,
                    "line": line_number,
                    "message": message,
                    "evidence": line.strip()[:500],
                })
    return findings


def analyze_file(root: Path, path_str: str, tracked: bool):
    path = root / path_str
    data = path.read_bytes()
    binary = is_binary_bytes(data, path)
    sensitive = is_sensitive(path_str)
    text = None
    symbols = {}
    risks = []
    if not binary:
        text = data.decode("utf-8", errors="replace")
        lines = text.splitlines()
        symbols = detect_symbols(text)
        risks = detect_risks(path_str, text)
        line_count = len(lines) if lines else (1 if text else 0)
        nonblank = sum(bool(line.strip()) for line in lines)
        max_len = max((len(line) for line in lines), default=0)
        trailing = sum(line.rstrip(" \t") != line for line in lines)
        tab_lines = sum("\t" in line for line in lines)
        chars = len(text)
        braces = (text.count("{"), text.count("}"), text.count("("), text.count(")"), text.count("["), text.count("]"))
    else:
        line_count = nonblank = max_len = trailing = tab_lines = chars = 0
        braces = (0, 0, 0, 0, 0, 0)
    record = FileRecord(
        path=path_str,
        tracked=tracked,
        size_bytes=len(data),
        sha256=hashlib.sha256(data).hexdigest(),
        extension=path.suffix.lower(),
        file_mode=stat.filemode(path.stat().st_mode),
        binary=binary,
        sensitive=sensitive,
        line_count=line_count,
        nonblank_lines=nonblank,
        character_count=chars,
        max_line_length=max_len,
        trailing_whitespace_lines=trailing,
        tab_lines=tab_lines,
        has_final_newline=(not data or data.endswith(b"\n")),
        open_braces=braces[0], close_braces=braces[1],
        open_parentheses=braces[2], close_parentheses=braces[3],
        open_brackets=braces[4], close_brackets=braces[5],
        symbol_count=sum(len(values) for values in symbols.values()),
        risk_count=len(risks),
    )
    return record, text, symbols, risks


def write_tsv(path: Path, rows: Iterable[dict[str, object]], fieldnames: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def gradle_patch_map(records: list[FileRecord], texts: dict[str, str]) -> list[dict[str, object]]:
    rows = []
    apply_re = re.compile(r"apply\s*\(\s*from\s*=\s*[\"']([^\"']+)[\"']\s*\)")
    target_re = re.compile(r"(?:projectDirectory\.file|rootProject\.file|file)\s*\(\s*[\"']([^\"']+)[\"']")
    task_re = re.compile(r"tasks\.(?:registering|register)\s*(?:\([^\)]*[\"']([^\"']+)[\"'])?")
    for record in records:
        if record.extension not in {".kts", ".gradle"}:
            continue
        text = texts.get(record.path, "")
        applies = apply_re.findall(text)
        targets = target_re.findall(text)
        write_calls = len(re.findall(r"\b(?:writeText|writeBytes|appendText)\s*\(", text))
        replace_calls = len(re.findall(r"\.replace\s*\(", text))
        tasks = [item for item in task_re.findall(text) if item]
        if applies or targets or write_calls or replace_calls or tasks:
            rows.append({
                "path": record.path,
                "applied_scripts": ",".join(applies),
                "target_paths": ",".join(sorted(set(targets))),
                "write_calls": write_calls,
                "replace_calls": replace_calls,
                "task_names": ",".join(sorted(set(tasks))),
            })
    return rows


def compare_inventories(before_path: Path, current_records: list[FileRecord], output: Path) -> None:
    if not before_path.exists():
        return
    before = {row["path"]: row for row in json.loads(before_path.read_text(encoding="utf-8"))}
    current = {record.path: asdict(record) for record in current_records}
    rows = []
    for path in sorted(set(before) | set(current)):
        left, right = before.get(path), current.get(path)
        status = "added" if left is None else "deleted" if right is None else "modified" if left["sha256"] != right["sha256"] else "unchanged"
        rows.append({
            "path": path,
            "status": status,
            "before_sha256": left["sha256"] if left else "",
            "after_sha256": right["sha256"] if right else "",
            "before_lines": left["line_count"] if left else "",
            "after_lines": right["line_count"] if right else "",
            "before_bytes": left["size_bytes"] if left else "",
            "after_bytes": right["size_bytes"] if right else "",
        })
    write_tsv(output / "comparison.tsv", rows, list(rows[0].keys()) if rows else ["path", "status"])
    counts = Counter(row["status"] for row in rows)
    md = ["# Source comparison", "", *(f"- **{key}**: {counts.get(key, 0)}" for key in ("added", "modified", "deleted", "unchanged")), "", "## Changed paths", ""]
    md.extend(f"- `{row['status']}` `{row['path']}`" for row in rows if row["status"] != "unchanged")
    (output / "comparison.md").write_text("\n".join(md) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--include-untracked", action="store_true")
    parser.add_argument("--compare-inventory")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)

    records = []
    texts = {}
    symbols_by_file = {}
    risks = []
    errors = []

    with (output / "full-text-index.txt").open("w", encoding="utf-8") as index_handle:
        for path_str, tracked in git_paths(args.include_untracked):
            path = root / path_str
            if not path.is_file():
                continue
            try:
                record, text, symbols, file_risks = analyze_file(root, path_str, tracked)
            except Exception as exc:
                errors.append({"path": path_str, "error": f"{type(exc).__name__}: {exc}"})
                continue
            records.append(record)
            symbols_by_file[path_str] = symbols
            risks.extend(file_risks)
            if text is not None:
                texts[path_str] = text
                index_handle.write(f"===== FILE: {path_str} =====\nSHA256: {record.sha256}\nLINES: {record.line_count}\nBYTES: {record.size_bytes}\n")
                if record.sensitive:
                    index_handle.write("CONTENT OMITTED FROM ARTIFACT: sensitive file; metadata and hash retained.\n\n")
                else:
                    index_handle.write(line_numbered(text) + "\n\n")

    records.sort(key=lambda item: item.path)
    inventory_rows = [asdict(record) for record in records]
    (output / "inventory.json").write_text(json.dumps(inventory_rows, ensure_ascii=False, indent=2), encoding="utf-8")
    if inventory_rows:
        write_tsv(output / "inventory.tsv", inventory_rows, list(inventory_rows[0].keys()))
    (output / "symbols.json").write_text(json.dumps(symbols_by_file, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "findings.json").write_text(json.dumps(risks, ensure_ascii=False, indent=2), encoding="utf-8")
    write_tsv(output / "findings.tsv", risks, ["severity", "code", "path", "line", "message", "evidence"])
    (output / "read-errors.json").write_text(json.dumps(errors, ensure_ascii=False, indent=2), encoding="utf-8")

    hashes = defaultdict(list)
    for record in records:
        hashes[record.sha256].append(record.path)
    duplicate_rows = [{"sha256": sha, "count": len(items), "paths": " | ".join(items)} for sha, items in sorted(hashes.items()) if len(items) > 1]
    write_tsv(output / "duplicate-files.tsv", duplicate_rows, ["sha256", "count", "paths"])

    patch_rows = gradle_patch_map(records, texts)
    write_tsv(output / "gradle-patch-map.tsv", patch_rows, ["path", "applied_scripts", "target_paths", "write_calls", "replace_calls", "task_names"])

    severity_counts = Counter(str(finding["severity"]) for finding in risks)
    extension_counts = Counter(record.extension or "<none>" for record in records)
    directory_counts = Counter(record.path.split("/", 1)[0] for record in records)
    totals = {
        "phase": args.phase,
        "git_head": run("git", "rev-parse", "HEAD").strip(),
        "git_branch": run("git", "rev-parse", "--abbrev-ref", "HEAD").strip(),
        "files": len(records),
        "tracked_files": sum(record.tracked for record in records),
        "untracked_files": sum(not record.tracked for record in records),
        "text_files": sum(not record.binary for record in records),
        "binary_files": sum(record.binary for record in records),
        "sensitive_files": sum(record.sensitive for record in records),
        "bytes": sum(record.size_bytes for record in records),
        "lines": sum(record.line_count for record in records),
        "nonblank_lines": sum(record.nonblank_lines for record in records),
        "symbols": sum(record.symbol_count for record in records),
        "findings": len(risks),
        "findings_by_severity": dict(sorted(severity_counts.items())),
        "files_by_extension": dict(sorted(extension_counts.items())),
        "files_by_top_directory": dict(sorted(directory_counts.items())),
        "gradle_mutation_scripts": sum(bool(row["write_calls"]) for row in patch_rows),
        "gradle_patch_rows": len(patch_rows),
        "read_errors": len(errors),
    }
    (output / "summary.json").write_text(json.dumps(totals, ensure_ascii=False, indent=2), encoding="utf-8")

    catalog = [
        f"# Exhaustive source audit — {args.phase}", "",
        f"- Commit: `{totals['git_head']}`",
        f"- Branch: `{totals['git_branch']}`",
        f"- Files: **{totals['files']}**",
        f"- Text files: **{totals['text_files']}**",
        f"- Binary files: **{totals['binary_files']}**",
        f"- Total lines: **{totals['lines']}**",
        f"- Total bytes: **{totals['bytes']}**",
        f"- Static findings: **{totals['findings']}**",
        f"- Read errors: **{totals['read_errors']}**", "",
        "## Severity totals", "",
    ]
    for severity in ("CRITICAL", "HIGH", "MEDIUM", "LOW"):
        catalog.append(f"- {severity}: **{severity_counts.get(severity, 0)}**")
    catalog += ["", "## File-by-file catalog", ""]
    risks_by_path = defaultdict(list)
    for finding in risks:
        risks_by_path[str(finding["path"])].append(finding)
    for record in records:
        catalog += [
            f"### `{record.path}`", "",
            f"- SHA-256: `{record.sha256}`",
            f"- Bytes/lines: `{record.size_bytes}` / `{record.line_count}`",
            f"- Type: `{'binary' if record.binary else 'text'}`; tracked=`{record.tracked}`; sensitive=`{record.sensitive}`",
            f"- Symbols: `{record.symbol_count}`; findings: `{record.risk_count}`",
            f"- Formatting: final_newline=`{record.has_final_newline}`, trailing_whitespace_lines=`{record.trailing_whitespace_lines}`, tab_lines=`{record.tab_lines}`",
            f"- Delimiters: braces=`{record.open_braces}/{record.close_braces}`, parentheses=`{record.open_parentheses}/{record.close_parentheses}`, brackets=`{record.open_brackets}/{record.close_brackets}`",
        ]
        symbols = symbols_by_file.get(record.path, {})
        symbol_summary = [f"{kind}={len(names)}" for kind, names in symbols.items() if names]
        if symbol_summary:
            catalog.append(f"- Declarations: `{', '.join(symbol_summary)}`")
        for finding in risks_by_path.get(record.path, [])[:20]:
            catalog.append(f"- [{finding['severity']}] line {finding['line']}: `{finding['code']}` — {finding['message']}")
        if len(risks_by_path.get(record.path, [])) > 20:
            catalog.append(f"- Additional findings: {len(risks_by_path[record.path]) - 20}; see `findings.tsv`.")
        catalog.append("")
    (output / "source-catalog.md").write_text("\n".join(catalog), encoding="utf-8")

    if args.compare_inventory:
        compare_inventories(Path(args.compare_inventory), records, output)

    print(json.dumps(totals, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
