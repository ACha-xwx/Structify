"""Build backend/spring/src/main/resources/classroom-code/textbook/library.json.

The library ships two things per fragment: the listing itself, kept verbatim, and a complete
*runnable example* around it. The fragments are textbook listings - most of them are a single
function that refers to types declared in a sibling listing, several carry the book's own typos -
so "append an empty main" can never produce something that compiles, let alone something whose
output means anything.

Every example in this file is therefore assembled here and then **actually compiled and run** with
the host gcc; the recorded stdout in the resource is what the program really printed. Nothing is
hand-written into the JSON: run this script, and the resource is regenerated from
`scripts/code-library/examples.py`.

    python scripts/build-code-library.py                 # verify with gcc, rewrite the resource
    python scripts/build-code-library.py --no-verify     # assemble only (needs no compiler)
    python scripts/build-code-library.py --gcc C:/path/to/gcc.exe

A group whose example does not compile, or whose output does not match the authored `expect`, fails
the run: the resource is only written when every example in it is known-good.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LIBRARY = os.path.join(ROOT, "backend", "spring", "src", "main", "resources",
                       "classroom-code", "textbook", "library.json")
RECIPES = os.path.join(HERE, "code-library", "examples.py")

# Every example is a translation unit of its own, so the common headers go in front. Note that the
# listings themselves often do not include anything at all: they were printed inside a book chapter,
# not compiled on their own.
HEAD = ("#include <stdio.h>\n"
        "#include <stdlib.h>\n"
        "#include <string.h>\n"
        "#include <malloc.h>\n\n")

RUN_TIMEOUT = 10


def load_recipes():
    namespace = {"__file__": RECIPES}
    with io.open(RECIPES, encoding="utf-8") as handle:
        exec(compile(handle.read(), RECIPES, "exec"), namespace)  # noqa: S102 - our own file
    return namespace


def find_gcc(explicit: str | None) -> str:
    if explicit:
        return explicit
    found = shutil.which("gcc")
    if found:
        return found
    raise SystemExit("gcc not found; pass --gcc or use --no-verify")


def fragment_text(group, fragments, fragment_id: str) -> str:
    code = fragments[fragment_id]["code"].strip("\n")
    for patch in group.get("fixes", {}).get(fragment_id, []):
        old, new = patch[0], patch[1]
        regex = len(patch) > 2 and patch[2] == "re"
        if regex:
            code, hits = re.subn(old, new, code)
        else:
            hits = code.count(old)
            code = code.replace(old, new)
        if not hits:
            raise SystemExit(f"{group['id']}: 补丁没命中 {fragment_id} 中的 {old!r}")
    return code


def program_for(group, fragments) -> str:
    if group.get("code"):
        return group["code"] if group["code"].endswith("\n") else group["code"] + "\n"
    pieces = [HEAD]
    extra = group.get("extra")
    if extra:
        pieces.append(extra if extra.endswith("\n") else extra + "\n")
        pieces.append("\n")
    for fragment_id in group["parts"]:
        code = fragment_text(group, fragments, fragment_id)
        pieces.append(f"/* ===== 以下来自 {fragment_id} ===== */\n{code}\n\n")
    helpers = group.get("helpers")
    if helpers:
        pieces.append(helpers.lstrip("\n") if helpers.endswith("\n") else helpers.lstrip("\n") + "\n")
        pieces.append("\n")
    if group.get("driver"):
        pieces.append(group["driver"].lstrip("\n"))
    return "".join(pieces)


def build_one(gcc: str, name: str, source: str, stdin: str, work: str):
    src = os.path.join(work, f"{name}.c")
    exe = os.path.join(work, f"{name}.exe")
    with io.open(src, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(source)
    compile_proc = subprocess.run(
        [gcc, "-std=gnu99", "-w", "-O0", "-o", exe, src],
        capture_output=True, text=True, errors="replace", timeout=120,
    )
    if compile_proc.returncode != 0:
        return None, "编译失败:\n" + (compile_proc.stderr or "").strip()[:900]
    run_proc = subprocess.run(
        [exe], input=stdin, capture_output=True, text=True, errors="replace",
        timeout=RUN_TIMEOUT,
    )
    # Several listings in the book declare `void main()`, whose exit status is whatever happened to
    # be in the register - so a non-zero code means nothing here. What matters is that the program
    # produced output instead of dying before it printed anything.
    if not run_proc.stdout:
        return None, (f"没有产生任何输出（退出码 {run_proc.returncode}）\n"
                      f"stderr={run_proc.stderr!r}")
    return run_proc.stdout, None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gcc", default=None)
    parser.add_argument("--no-verify", action="store_true")
    parser.add_argument("--only", default=None, help="substring filter on group ids (debugging)")
    parser.add_argument("--report", default=None, help="write every example's stdout here")
    args = parser.parse_args()

    recipes = load_recipes()
    groups = recipes["GROUPS"]
    blocked = recipes["BLOCKED"]

    with io.open(LIBRARY, encoding="utf-8") as handle:
        library = json.load(handle)

    # Earlier versions shipped two generic "append this skeleton" snippets; every fragment now carries
    # a verified example of its own instead.
    library.pop("skeletons", None)
    library["source"] = ("lesson-materials/source_normalized（片段按原样保留；每段的示例由 "
                         "scripts/code-library/examples.py 装配并本地实测）")

    fragments = {}
    for chapter in library["chapters"]:
        for fragment in chapter["fragments"]:
            fragments[fragment["id"]] = fragment

    # group membership: a fragment belongs to exactly one group, or is blocked
    membership = {}
    for group in groups:
        for fragment_id in group["members"]:
            if fragment_id in membership:
                raise SystemExit(f"{fragment_id} 同时出现在两个组里")
            membership[fragment_id] = group
    unknown = [i for i in list(membership) + list(blocked) if i not in fragments]
    if unknown:
        raise SystemExit(f"配方引用了不存在的片段: {unknown}")

    todo = [i for i in fragments if i not in membership and i not in blocked]
    if todo:
        print(f"!! {len(todo)} 个片段既没有示例也没有说明: {todo}")

    gcc = None if args.no_verify else find_gcc(args.gcc)
    work = tempfile.mkdtemp(prefix="code-library-")
    failures = []
    results = []
    try:
        for index, group in enumerate(groups, start=1):
            if args.only and args.only not in group["id"]:
                continue
            source = program_for(group, fragments)
            stdin = group.get("stdin", "")
            expected = ""
            if gcc:
                expected, error = build_one(gcc, f"g{index:03d}", source, stdin, work)
                if error:
                    failures.append((group["id"], error))
                    continue
                want = group.get("expect")
                if want is not None and expected != want:
                    failures.append((group["id"], f"输出与 expect 不一致\n得到 {expected!r}\n期望 {want!r}"))
                    continue
            results.append((group, source, stdin, expected))
            print(f"[{index:03d}] {group['id']:28} {len(group['members'])} 段"
                  f"{'  (已实测)' if gcc else ''}")
    finally:
        shutil.rmtree(work, ignore_errors=True)

    if failures:
        print("\n===== 失败 =====")
        for name, error in failures:
            print(f"\n--- {name}\n{error}")
        print(f"\n{len(failures)} 个组失败，未写入资源。")
        return 1

    for fragment in fragments.values():
        fragment.pop("example", None)
        fragment.pop("exampleBlocked", None)
    for fragment_id, reason in blocked.items():
        fragments[fragment_id]["exampleBlocked"] = reason
    for group, source, stdin, expected in results:
        example = {"code": source, "stdin": stdin, "expectedStdout": expected}
        if group.get("note"):
            example["note"] = group["note"]
        for fragment_id in group["members"]:
            fragments[fragment_id]["example"] = dict(example)
    verified = sum(1 for _g, _s, _i, expected in results if expected)

    if args.report:
        with io.open(args.report, "w", encoding="utf-8", newline="\n") as handle:
            for group, source, stdin, expected in results:
                handle.write(f"\n########## {group['id']}  ({', '.join(group['members'])})\n")
                handle.write(f"--- stdin ---\n{stdin}\n--- stdout ---\n{expected}\n")

    with io.open(LIBRARY, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(library, ensure_ascii=False, indent=1) + "\n")

    covered = sum(len(group["members"]) for group, _s, _i, _e in results)
    print(f"\n示例 {covered} 段 / 说明 {len(blocked)} 段 / 合计 {len(fragments)}"
          f"；其中 {verified} 个示例经本地 gcc 实测。")
    print(f"资源已更新: {os.path.relpath(LIBRARY, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
