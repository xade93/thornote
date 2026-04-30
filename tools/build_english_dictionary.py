#!/usr/bin/env python3
import argparse
import json
import re
import sqlite3
import zipfile
from pathlib import Path


POS_FILES = {
    "noun": "noun",
    "verb": "verb",
    "adj": "adjective",
    "adv": "adverb",
}

QUOTE_PATTERNS = [
    re.compile(r'"([^"]+)"'),
    re.compile(r"“([^”]+)”"),
]


def normalize_lemma(value: str) -> str:
    return value.replace("_", " ").strip().lower()


def parse_gloss(gloss: str) -> tuple[str, list[str]]:
    examples: list[str] = []
    cleaned = gloss
    for pattern in QUOTE_PATTERNS:
        examples.extend(match.strip() for match in pattern.findall(cleaned))
        cleaned = pattern.sub("", cleaned)
    definition = re.sub(r"\s+", " ", cleaned).strip(" ;")
    return definition, examples


def parse_data_line(line: str, pos: str) -> list[tuple[str, str, str, str, str]]:
    if not line.strip() or line.startswith("  "):
        return []

    head, _, gloss = line.partition("|")
    fields = head.split()
    if len(fields) < 5:
        return []

    word_count = int(fields[3], 16)
    cursor = 4
    words: list[str] = []
    for _ in range(word_count):
        if cursor + 1 >= len(fields):
            return []
        words.append(fields[cursor].replace("_", " "))
        cursor += 2

    definition, examples = parse_gloss(gloss)
    if not definition:
        return []

    synonyms = json.dumps(words, ensure_ascii=False)
    examples_json = json.dumps(examples, ensure_ascii=False)

    rows = []
    for word in words:
        rows.append((normalize_lemma(word), word, pos, definition, examples_json, synonyms))
    return rows


def read_zip_text(zf: zipfile.ZipFile, name: str) -> str:
    with zf.open(name) as handle:
        return handle.read().decode("utf-8")


def build_database(source_zip: Path, output_db: Path) -> None:
    if output_db.exists():
        output_db.unlink()
    output_db.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(output_db)
    conn.execute("PRAGMA journal_mode=OFF")
    conn.execute("PRAGMA synchronous=OFF")
    conn.execute(
        """
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            lemma_key TEXT NOT NULL,
            display_word TEXT NOT NULL,
            pos TEXT NOT NULL,
            definition TEXT NOT NULL,
            examples_json TEXT NOT NULL,
            synonyms_json TEXT NOT NULL
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE exceptions (
            inflected TEXT NOT NULL,
            lemma_key TEXT NOT NULL
        )
        """
    )

    with zipfile.ZipFile(source_zip) as zf:
        root = "oewn2024"
        entry_rows = []
        for suffix, pos in POS_FILES.items():
            for line in read_zip_text(zf, f"{root}/data.{suffix}").splitlines():
                entry_rows.extend(parse_data_line(line, pos))

        conn.executemany(
            """
            INSERT INTO entries (
                lemma_key, display_word, pos, definition, examples_json, synonyms_json
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            entry_rows,
        )

        exception_rows = []
        for suffix in POS_FILES:
            exc_name = f"{root}/{suffix}.exc"
            try:
                lines = read_zip_text(zf, exc_name).splitlines()
            except KeyError:
                continue
            for line in lines:
                parts = line.split()
                if len(parts) >= 2:
                    inflected = normalize_lemma(parts[0])
                    for base in parts[1:]:
                        exception_rows.append((inflected, normalize_lemma(base)))

        conn.executemany(
            "INSERT INTO exceptions (inflected, lemma_key) VALUES (?, ?)",
            exception_rows,
        )

    conn.execute("CREATE INDEX idx_entries_lemma ON entries(lemma_key)")
    conn.execute("CREATE INDEX idx_exceptions_inflected ON exceptions(inflected)")
    conn.execute("CREATE INDEX idx_entries_pos ON entries(pos)")
    conn.commit()
    conn.execute("VACUUM")
    conn.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output_db", type=Path)
    args = parser.parse_args()
    build_database(args.source_zip, args.output_db)


if __name__ == "__main__":
    main()
