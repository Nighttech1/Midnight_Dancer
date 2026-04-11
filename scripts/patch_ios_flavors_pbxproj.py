#!/usr/bin/env python3
"""Add iOS flavor build configurations to Runner.xcodeproj/project.pbxproj."""
from __future__ import annotations

import re
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PBX = ROOT / "ios" / "Runner.xcodeproj" / "project.pbxproj"

FLAVORS = ("lite", "standard", "full", "english")
MODES = ("Debug", "Release", "Profile")


def nid() -> str:
    return uuid.uuid4().hex[:24].upper()


def main() -> None:
    text = PBX.read_text(encoding="utf-8")
    if "Debug-lite.xcconfig" in text and "name = Debug-lite;" in text:
        print("project.pbxproj already contains iOS flavor configs; skip.")
        return

    file_refs: list[str] = []
    fr_ids: dict[tuple[str, str], str] = {}
    for flavor in FLAVORS:
        for mode in MODES:
            rid = nid()
            fr_ids[(flavor, mode)] = rid
            fname = f"{mode}-{flavor}.xcconfig"
            file_refs.append(
                f"\t\t{rid} /* {fname} */ = {{isa = PBXFileReference; lastKnownFileType = text.xcconfig; name = \"{fname}\"; path = Flutter/{fname}; sourceTree = \"<group>\"; }};"
            )

    insert_fr = "\n".join(file_refs) + "\n"
    marker = "\t\t9740EEB21CF90195004384FC /* Debug.xcconfig */ = {isa = PBXFileReference;"
    if marker not in text:
        raise SystemExit("Could not find Debug.xcconfig PBXFileReference anchor")
    text = text.replace(marker, insert_fr + marker, 1)

    flutter_group_needle = "\t\t9740EEB11CF90186004384FC /* Flutter */ = {\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n"
    if flutter_group_needle not in text:
        raise SystemExit("Could not find Flutter PBXGroup")
    child_lines = []
    for flavor in FLAVORS:
        for mode in MODES:
            rid = fr_ids[(flavor, mode)]
            fname = f"{mode}-{flavor}.xcconfig"
            child_lines.append(f"\t\t\t\t{rid} /* {fname} */,")
    text = text.replace(flutter_group_needle, flutter_group_needle + "\n".join(child_lines) + "\n", 1)

    proj_pat = {
        "Debug": r"(97C147031CF9000F007C117D /\* Debug \*/ = \{[\s\S]*?name = Debug;\n\t\t\};)",
        "Release": r"(97C147041CF9000F007C117D /\* Release \*/ = \{[\s\S]*?name = Release;\n\t\t\};)",
        "Profile": r"(249021D3217E4FDB00AE95B9 /\* Profile \*/ = \{[\s\S]*?name = Profile;\n\t\t\};)",
    }
    run_pat = {
        "Debug": r"(97C147061CF9000F007C117D /\* Debug \*/ = \{[\s\S]*?name = Debug;\n\t\t\};)",
        "Release": r"(97C147071CF9000F007C117D /\* Release \*/ = \{[\s\S]*?name = Release;\n\t\t\};)",
        "Profile": r"(249021D4217E4FDB00AE95B9 /\* Profile \*/ = \{[\s\S]*?name = Profile;\n\t\t\};)",
    }
    test_pat = {
        "Debug": r"(331C8088294A63A400263BE5 /\* Debug \*/ = \{[\s\S]*?name = Debug;\n\t\t\};)",
        "Release": r"(331C8089294A63A400263BE5 /\* Release \*/ = \{[\s\S]*?name = Release;\n\t\t\};)",
        "Profile": r"(331C808A294A63A400263BE5 /\* Profile \*/ = \{[\s\S]*?name = Profile;\n\t\t\};)",
    }

    def dup_block(
        src: str, old_id: str, new_id: str, new_name: str, base_old: str | None, base_new: str | None
    ) -> str:
        out = src.replace(old_id, new_id, 1)
        out = re.sub(r"name = (Debug|Release|Profile);", f"name = {new_name};", out, count=1)
        if base_old and base_new:
            out = out.replace(base_old, base_new, 1)
        return out

    new_blocks: list[str] = []
    cfg_proj: list[str] = []
    cfg_run: list[str] = []
    cfg_test: list[str] = []

    for flavor in FLAVORS:
        for mode in MODES:
            new_name = f"{mode}-{flavor}"
            pid, rid, tid = nid(), nid(), nid()
            frid = fr_ids[(flavor, mode)]

            m_proj = re.search(proj_pat[mode], text)
            m_run = re.search(run_pat[mode], text)
            m_test = re.search(test_pat[mode], text)
            if not m_proj or not m_run or not m_test:
                raise SystemExit(f"Could not match source block for {mode}")

            proj_src = m_proj.group(1)
            run_src = m_run.group(1)
            test_src = m_test.group(1)

            old_proj_id = proj_src.split()[0]
            old_run_id = run_src.split()[0]
            old_test_id = test_src.split()[0]

            if mode == "Debug":
                br_old = "9740EEB21CF90195004384FC /* Debug.xcconfig */"
            else:
                br_old = "7AFA3C8E1D35360C0083082E /* Release.xcconfig */"
            br_new = f"{frid} /* {new_name}.xcconfig */"

            new_blocks.append(dup_block(proj_src, old_proj_id, pid, new_name, None, None))
            new_blocks.append(dup_block(run_src, old_run_id, rid, new_name, br_old, br_new))
            new_blocks.append(dup_block(test_src, old_test_id, tid, new_name, None, None))

            cfg_proj.append(pid)
            cfg_run.append(rid)
            cfg_test.append(tid)

    insert_cfg = "\n".join(new_blocks) + "\n"
    end_marker = "/* End XCBuildConfiguration section */"
    text = text.replace(end_marker, insert_cfg + end_marker, 1)

    def patch_list(s: str, label: str, ids: list[str]) -> str:
        pat = rf'(/\* {re.escape(label)} \*/ = {{\n\t\t\tisa = XCConfigurationList;\n\t\t\tbuildConfigurations = \(\n)([\s\S]*?)(\t\t\t\);\n\t\t\tdefaultConfigurationIsVisible)'
        m = re.search(pat, s)
        if not m:
            raise SystemExit(f"XCConfigurationList not found: {label}")
        extra = "".join(f"\t\t\t\t{i} /* flavor */,\n" for i in ids)
        return s[: m.start(2)] + m.group(2) + extra + s[m.end(2) :]

    text = patch_list(text, 'Build configuration list for PBXProject "Runner"', cfg_proj)
    text = patch_list(text, 'Build configuration list for PBXNativeTarget "Runner"', cfg_run)
    text = patch_list(text, 'Build configuration list for PBXNativeTarget "RunnerTests"', cfg_test)

    PBX.write_text(text, encoding="utf-8", newline="\n")
    print("Patched", PBX)


if __name__ == "__main__":
    main()
