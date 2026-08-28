#!/usr/bin/env python3
"""Turns one CHANGELOG section into the release body the phone can read.

The in-app update dialog renders a release body as plain text, so Markdown
reaches it verbatim: `**` and `###` arrive as punctuation. The dialog also wraps
text itself, which turns the CHANGELOG's own hard wrapping into ragged short
lines. Both were fixed by hand for 1.11.0 — and hand-fixing is exactly the step
that gets skipped when a release is published by pressing a button.

Usage: release_notes.py CHANGELOG.md 1.12.0
"""

import re
import sys

HEADING = re.compile(r"^## \[(?P<version>[^\]]+)\]")
SUBHEADING = re.compile(r"^#{3,6}\s+(?P<text>.+)$")
BULLET = re.compile(r"^(?P<indent> *)[-*]\s+(?P<text>.+)$")
LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")


def strip_inline(text: str) -> str:
    """Drops the Markup that would otherwise be read out as punctuation."""
    return LINK.sub(r"\1", text).replace("**", "").replace("`", "")


def section(lines: list[str], version: str) -> list[str]:
    """The lines of one version's section, without its own heading."""
    body: list[str] = []
    inside = False
    for line in lines:
        heading = HEADING.match(line)
        if heading:
            if inside:
                break
            inside = heading.group("version") == version
            continue
        if inside:
            body.append(line.rstrip())
    if not inside:
        raise SystemExit(
            f"В CHANGELOG.md нет раздела «## [{version}]». Перенесите в него то, "
            "что накопилось в «Unreleased», и повторите."
        )
    return body


def to_plain_text(body: list[str]) -> str:
    """Unwraps paragraphs and bullets; CHANGELOG wraps at 95 columns, the dialog does not."""
    out: list[str] = []
    pending: str | None = None

    def flush() -> None:
        nonlocal pending
        if pending is not None:
            out.append(pending)
            pending = None

    for line in body:
        stripped = line.strip()
        if not stripped:
            flush()
            if out and out[-1] != "":
                out.append("")
            continue

        subheading = SUBHEADING.match(stripped)
        if subheading:
            flush()
            if out and out[-1] != "":
                out.append("")
            out.append(strip_inline(subheading.group("text")))
            out.append("")
            continue

        bullet = BULLET.match(line)
        if bullet:
            flush()
            marker = "  – " if len(bullet.group("indent")) >= 2 else "• "
            pending = marker + strip_inline(bullet.group("text"))
            continue

        # A continuation of whatever came before: the CHANGELOG's own line wrapping.
        text = strip_inline(stripped)
        pending = f"{pending} {text}" if pending else text

    flush()
    while out and out[0] == "":
        out.pop(0)
    while out and out[-1] == "":
        out.pop()
    return "\n".join(out) + "\n"


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    changelog, version = sys.argv[1], sys.argv[2]
    with open(changelog, encoding="utf-8") as handle:
        lines = handle.read().splitlines()
    # Written as bytes on purpose: on Windows `sys.stdout` follows the console code page,
    # so a redirect there produced a cp1251 file out of a UTF-8 changelog.
    sys.stdout.buffer.write(to_plain_text(section(lines, version)).encode("utf-8"))


if __name__ == "__main__":
    main()
