"""Build backend/spring/src/main/resources/classroom-code/lessons.json.

Water flows one way here: every classroom sample is a textbook listing whose complete program was
already compiled and run by `build-code-library.py`, recorded with the output it really produced.
This script only decides **which** listing belongs to **which** reviewed lesson, so nothing in the
resource can claim to run without proving it did.

The hand-built pilot (03-01) is the exception: its four samples drive several listings at once with
a driver nobody else has, so they are kept verbatim in `code-library/authored-lessons.json` and
merged in front of whatever the map adds.

    python scripts/build-classroom-lessons.py
"""

from __future__ import annotations

import datetime as dt
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RESOURCES = os.path.join(ROOT, "backend", "spring", "src", "main", "resources")
TARGET = os.path.join(RESOURCES, "classroom-code", "lessons.json")
LIBRARY = os.path.join(RESOURCES, "classroom-code", "textbook", "library.json")
AUTHORED = os.path.join(HERE, "code-library", "authored-lessons.json")
MAP = os.path.join(HERE, "code-library", "lesson-map.py")
REVIEWED = os.path.join(ROOT, "private", "reviewed-textbook", "lessons")

CHAPTER_SLUGS = {
    "01": "intro",
    "02": "linear-list",
    "03": "stack-queue",
    "04": "string",
    "05": "array-glist",
    "06": "tree",
    "07": "graph",
    "08": "search",
    "09": "sort",
    "10": "external-sort",
}

KEY = re.compile(r"^(\d{2})-(\d{2})-(.+?)-已核验")


def load_mapping() -> dict:
    namespace = {"__file__": MAP}
    with io.open(MAP, encoding="utf-8") as handle:
        exec(compile(handle.read(), MAP, "exec"), namespace)  # noqa: S102 - our own file
    return namespace["LESSONS"]


def reviewed_titles() -> dict:
    """`01-01-绪论-数据结构的基础概念-已核验页1-6.md` -> `01-01`: 绪论-数据结构的基础概念."""
    titles = {}
    if not os.path.isdir(REVIEWED):
        raise SystemExit(f"找不到已核验课时目录: {REVIEWED}")
    for name in sorted(os.listdir(REVIEWED)):
        match = KEY.match(name)
        if match:
            titles.setdefault("%s-%s" % (match.group(1), match.group(2)), match.group(3))
    return titles


def source_file_of(fragment: dict, chapter_number: str) -> str:
    """Where the listing sits in the book's source tree: `3.12.c` -> `ch03/code/3.12.c`."""
    return "ch%s/code/%s" % (chapter_number, fragment["file"])


def main() -> int:
    lessons_map = load_mapping()
    titles = reviewed_titles()

    with io.open(LIBRARY, encoding="utf-8") as handle:
        library = json.load(handle)

    fragments = {}
    for chapter in library["chapters"]:
        number = re.sub(r"\D", "", chapter["chapter"])
        for fragment in chapter["fragments"]:
            previous = fragments.get(fragment["id"])
            if previous:
                raise SystemExit(f"片段 id 重复: {fragment['id']}")
            fragments[fragment["id"]] = (number, fragment)

    with io.open(AUTHORED, encoding="utf-8") as handle:
        authored = json.load(handle)

    lessons = []
    total = 0
    for key in sorted(set(lessons_map) | set(authored)):
        picked = lessons_map.get(key, [])
        handmade = authored.get(key, [])
        samples = [dict(sample) for sample in handmade]
        for index, (fragment_id, title) in enumerate(picked, start=1):
            entry = fragments.get(fragment_id)
            if entry is None:
                raise SystemExit(f"{key} 引用了不存在的片段: {fragment_id}")
            number, fragment = entry
            example = fragment.get("example")
            if not example:
                raise SystemExit(f"{key} 引用的 {fragment_id} 没有可运行示例")
            samples.append({
                "id": "%s-s%d" % (key, len(handmade) + index),
                "title": title,
                "file": source_file_of(fragment, number),
                "sections": [],
                "targets": [],
                "summary": example.get("note", ""),
                "stdin": example.get("stdin", ""),
                "expectedStdout": example.get("expectedStdout", ""),
                "code": example.get("code", ""),
            })

        if not samples:
            continue
        for sample in samples:
            if not re.search(r"\b(int\s+main\s*\([^)]*\)|void\s+main\s*\(\))", sample["code"]):
                raise SystemExit(f"{key} 的 {sample['id']} 不是一个完整程序")
            if "#include <stdio.h>" not in sample["code"]:
                raise SystemExit(f"{key} 的 {sample['id']} 不是一个完整程序")
            if not sample["expectedStdout"]:
                raise SystemExit(f"{key} 的 {sample['id']} 没有记录期望输出")

        total += len(samples)
        lessons.append({
            "coursewareKey": key,
            "lessonTitle": titles.get(key, key),
            "chapterId": "%s-%s" % (key[:2], CHAPTER_SLUGS.get(key[:2], "chapter")),
            "samples": samples,
        })

    payload = {
        "version": 1,
        "builtAt": dt.datetime.now().astimezone().strftime("%Y-%m-%dT%H:%M:%S%z"),
        "source": ("教材样例由 scripts/build-classroom-lessons.py 依据 "
                   "classroom-code/textbook/library.json 与 code-library/lesson-map.py 生成；"
                   "代码与期望输出全部来自书中已编译运行过的示例，03-01 的前四条为手工编排的样板。"),
        "lessons": lessons,
    }
    with io.open(TARGET, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    print(f"课时 {len(lessons)} 个，样例 {total} 条 -> {os.path.relpath(TARGET, ROOT)}")
    missing = sorted(key for key in titles if key not in lessons_map and key not in authored)
    print(f"没有可运行样例的课时 {len(missing)} 个: {' '.join(missing)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
