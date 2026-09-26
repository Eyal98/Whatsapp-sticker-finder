#!/usr/bin/env python3
"""Reports native libraries in an APK that can't load on phones with 16 KB memory pages.

Android 15+ devices may use 16 KB pages; a library whose loadable (PT_LOAD) segments are aligned
to less than 16 KB fails to load there. The APK stores libraries compressed (they're extracted at
install), so only the ELF alignment matters, not the zip alignment.

Usage: scripts/check-16kb-pages.py path/to/app.apk [--strict]
Prints one line per library; with --strict, exits 1 if any is under 16 KB.
"""
import struct
import sys
import zipfile

PT_LOAD = 1
PAGE = 16 * 1024


def min_load_alignment(data: bytes):
    if data[:4] != b"\x7fELF" or data[4] != 2:  # 64-bit ELF only (arm64-v8a)
        return None
    endian = "<" if data[5] == 1 else ">"
    phoff, = struct.unpack_from(endian + "Q", data, 0x20)
    phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type, = struct.unpack_from(endian + "I", data, off)
        if p_type == PT_LOAD:
            aligns.append(struct.unpack_from(endian + "Q", data, off + 0x30)[0])
    return min(aligns) if aligns else None


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    strict = "--strict" in sys.argv
    if len(args) != 1:
        print(__doc__)
        return 2
    bad = []
    with zipfile.ZipFile(args[0]) as apk:
        for name in sorted(n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")):
            align = min_load_alignment(apk.read(name))
            ok = align is not None and align >= PAGE
            print(f"{'ok  ' if ok else 'FAIL'} {name}: alignment {align}")
            if not ok:
                bad.append(name)
    if bad:
        print(f"{len(bad)} librar{'y' if len(bad) == 1 else 'ies'} won't load on 16 KB-page phones")
        # A GitHub Actions warning annotation, so it shows on the run summary.
        print(f"::warning::{len(bad)} native libraries aren't 16 KB-page compatible: {', '.join(bad)}")
        return 1 if strict else 0
    print("All native libraries support 16 KB pages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
