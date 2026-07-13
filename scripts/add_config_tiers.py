#!/usr/bin/env python3
"""Inject socket-tiers and per-gem tier into GemInfusion config YAML."""
from __future__ import annotations

import re
import sys
from pathlib import Path

TIER_BY_SOCKET = {
    "Basic Gemstone": 1,
    "Polished Gemstone": 2,
    "Radiant Gemstone": 3,
    "Mythical Gemstone": 4,
}

SOCKET_TIERS_BLOCK = """socket-tiers:
    "Basic Gemstone": 1
    "Polished Gemstone": 2
    "Radiant Gemstone": 3
    "Mythical Gemstone": 4
    "Uncolored": 0

"""


def insert_socket_tiers(text: str) -> str:
    if "socket-tiers:" in text:
        return text
    marker = "infusion_item:"
    idx = text.find(marker)
    if idx == -1:
        return SOCKET_TIERS_BLOCK + text
    line_end = text.find("\n", idx)
    if line_end == -1:
        return text + "\n\n" + SOCKET_TIERS_BLOCK
    return text[: line_end + 1] + "\n" + SOCKET_TIERS_BLOCK + text[line_end + 1 :]


def add_gem_tiers(text: str) -> str:
    lines = text.splitlines()
    out: list[str] = []
    in_gems = False
    tier_added = False

    for line in lines:
        if re.match(r"^gems:\s*$", line):
            in_gems = True
            tier_added = False
            out.append(line)
            continue

        if in_gems and re.match(r"^[a-z_]+:\s*$", line) and not line.startswith("    "):
            in_gems = False
            tier_added = False

        if in_gems and re.match(r"^    [a-z_]+:\s*$", line):
            tier_added = False

        socket_match = re.match(r"^(\s+)socket_colour:\s*(.+?)\s*$", line)
        if in_gems and socket_match and not tier_added:
            indent = socket_match.group(1)
            colour = socket_match.group(2).strip().strip('"').strip("'")
            tier = TIER_BY_SOCKET.get(colour, 1)
            out.append(f"{indent}tier: {tier}")
            tier_added = True

        out.append(line)

    return "\n".join(out) + ("\n" if text.endswith("\n") else "")


def main() -> int:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "config_input.yml"
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).parent.parent / "src/main/resources/config.yml"
    text = src.read_text(encoding="utf-8")
    text = insert_socket_tiers(text)
    text = add_gem_tiers(text)
    dst.write_text(text, encoding="utf-8")
    print(f"Wrote {dst}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
