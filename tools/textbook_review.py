# Renders textbook pages for manual verification into the private workspace.
# Usage: python tools/textbook_review.py <firstTextbookPage> <lastTextbookPage> [dpi]
import os
import sys

import pymupdf

PDF = r"D:\Downloads\数据结构：用C语言描述  第2版【耿国华】高等教育出版社2015.7-9787040433050_ (2).pdf"
OFFSET = 11
OUT = r"C:\Users\ROG\AppData\Local\Temp\structify-verify\pages"


def main() -> None:
    first = int(sys.argv[1])
    last = int(sys.argv[2])
    dpi = int(sys.argv[3]) if len(sys.argv) > 3 else 170
    os.makedirs(OUT, exist_ok=True)
    document = pymupdf.open(PDF)
    for textbook_page in range(first, last + 1):
        pdf_page = textbook_page + OFFSET
        pixmap = document[pdf_page - 1].get_pixmap(dpi=dpi)
        target = os.path.join(OUT, f"tb{textbook_page:03d}_pdf{pdf_page:03d}.png")
        pixmap.save(target)
        print(textbook_page, os.path.basename(target), os.path.getsize(target) // 1024, "KB")


if __name__ == "__main__":
    main()
