# Zoomed re-OCR of a region of a textbook page.
#
# The page-level OCR sometimes loses small glyphs: circled step numbers (① ② ③), subscripts and
# labels inside diagrams. This tool re-renders the region at a higher resolution and OCRs it alone,
# which recovers most of them.
#
# Usage: python tools/textbook_ocr_zoom.py <textbookPage> <x> <y> <x1> <y1> [zoom=3] [dpi=600]
# The coordinates are the ones printed by tools/textbook_ocr.py (scaled to a 2400 px wide page).
import json
import os
import sys

import pymupdf

PDF = r"D:\Downloads\数据结构：用C语言描述  第2版【耿国华】高等教育出版社2015.7-9787040433050_ (2).pdf"
OFFSET = 11
ROOT = r"C:\Users\ROG\AppData\Local\Temp\structify-verify"
TOOLS = os.path.join(ROOT, "ocrtools")
TARGET_WIDTH = 2400


def main() -> None:
    page = int(sys.argv[1])
    x, y, x1, y1 = (float(value) for value in sys.argv[2:6])
    zoom = float(sys.argv[6]) if len(sys.argv) > 6 else 3.0
    dpi = int(sys.argv[7]) if len(sys.argv) > 7 else 600

    sys.path.insert(0, TOOLS)
    from rapidocr_onnxruntime import RapidOCR

    document = pymupdf.open(PDF)
    page_rect = document[page + OFFSET - 1].rect
    scale = page_rect.width / TARGET_WIDTH
    clip = pymupdf.Rect(x * scale, y * scale, x1 * scale, y1 * scale)
    zoomed = document[page + OFFSET - 1].get_pixmap(dpi=int(dpi * zoom), clip=clip)
    out_dir = os.path.join(ROOT, "zoom")
    os.makedirs(out_dir, exist_ok=True)
    png = os.path.join(out_dir, f"tb{page:03d}_{int(x)}_{int(y)}.png")
    zoomed.save(png)

    engine = RapidOCR()
    result, _ = engine(png)
    lines = []
    for box, text, score in result or []:
        ys = [point[1] for point in box]
        xs = [point[0] for point in box]
        lines.append({"y": round(min(ys)), "x": round(min(xs)), "t": text, "s": round(float(score), 3)})
    lines.sort(key=lambda item: (item["y"], item["x"]))
    print(json.dumps(lines, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
