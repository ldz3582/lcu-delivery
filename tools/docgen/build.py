# -*- coding: utf-8 -*-
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from docx_kit import Handbook
from content_a import cover, overview, map_page, chapter1, chapter2
from content_b import chapter3, chapter4
from content_c import chapter5, chapter6, chapter7, chapter8, chapter9, appendix

_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(_ROOT, "docs", "design", "聊大外卖小程序_开发行动手册.docx")


def main():
    h = Handbook(footer_text="聊城大学校园外卖小程序 · 开发行动手册")
    cover(h)
    overview(h)
    map_page(h)
    chapter1(h)
    chapter2(h)
    chapter3(h)
    chapter4(h)
    chapter5(h)
    chapter6(h)
    chapter7(h)
    chapter8(h)
    chapter9(h)
    appendix(h)
    h.save(OUT)
    print("saved:", OUT)


if __name__ == "__main__":
    main()
