# High-resolution OCR for textbook verification.
#
# The scanned PDF has no text layer and the review environment has no image input, so every page is
# re-OCR'd at high DPI and the recognized boxes are emitted with their coordinates. Coordinates let
# the reviewer rebuild the reading order, identify figure captions and read labels inside diagrams
# instead of relying on the much lower-quality page-level OCR baseline.
#
# Usage: python tools/textbook_ocr.py <firstTextbookPage> <lastTextbookPage> [dpi]
# Output: C:\Users\ROG\AppData\Local\Temp\structify-verify\ocr<dpi>\tb<NNN>.json   (line list)
#         C:\Users\ROG\AppData\Local\Temp\structify-verify\ocr<dpi>\tb<NNN>.txt    (reading order)
import json
import os
import sys

import pymupdf

PDF = r"D:\Downloads\数据结构：用C语言描述  第2版【耿国华】高等教育出版社2015.7-9787040433050_ (2).pdf"
OFFSET = 11
ROOT = r"C:\Users\ROG\AppData\Local\Temp\structify-verify"
TOOLS = os.environ.get("STRUCTIFY_OCR_TOOLS", os.path.join(ROOT, "ocrtools"))
TARGET_WIDTH = 2400


def build_engine(dpi: int):
    sys.path.insert(0, TOOLS)
    from rapidocr_onnxruntime import RapidOCR

    return RapidOCR()


def main() -> None:
    first = int(sys.argv[1])
    last = int(sys.argv[2])
    dpi = int(sys.argv[3]) if len(sys.argv) > 3 else 400
    out_dir = os.path.join(ROOT, f"ocr{dpi}")
    os.makedirs(out_dir, exist_ok=True)
    engine = build_engine(dpi)
    document = pymupdf.open(PDF)
    for textbook_page in range(first, last + 1):
        pdf_page = textbook_page + OFFSET
        pixmap = document[pdf_page - 1].get_pixmap(dpi=dpi)
        png = os.path.join(out_dir, f"tb{textbook_page:03d}.png")
        pixmap.save(png)
        result, _ = engine(png)
        lines = []
        for box, text, score in result or []:
            xs = [point[0] for point in box]
            ys = [point[1] for point in box]
            lines.append({
                "x": round(min(xs)),
                "y": round(min(ys)),
                "x1": round(max(xs)),
                "y1": round(max(ys)),
                "t": text,
                "s": round(float(score), 3),
            })
        lines.sort(key=lambda item: (item["y"], item["x"]))
        with open(os.path.join(out_dir, f"tb{textbook_page:03d}.json"), "w", encoding="utf-8") as handle:
            json.dump(lines, handle, ensure_ascii=False, indent=1)
        scale = TARGET_WIDTH / pixmap.width
        with open(os.path.join(out_dir, f"tb{textbook_page:03d}.txt"), "w", encoding="utf-8") as handle:
            for item in lines:
                handle.write(f'[{round(item["x"] * scale):4d},{round(item["y"] * scale):4d}] {item["t"]}\n')
        print(f"textbook page {textbook_page} -> {len(lines)} lines", flush=True)


if __name__ == "__main__":
    main()
