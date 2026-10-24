"""docx 渲染工具：面向本手册的极简排版 DSL。"""
import re

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor, Emu

# ---------- 配色 ----------
INK = RGBColor(0x1A, 0x1D, 0x21)
MUTED = RGBColor(0x5B, 0x64, 0x70)
ACCENT = RGBColor(0xB4, 0x3C, 0x07)
ACCENT_HEX = "B43C07"
ACCENT_TINT = "FDF2EA"
NOTE_HEX = "1F4E79"
NOTE_TINT = "EAF2FB"
OK_HEX = "1E6B3A"
OK_TINT = "EAF5EE"
TABLE_HEAD_HEX = "263238"
ROW_ALT_HEX = "F7F9FA"
BORDER_HEX = "DCE1E7"
CODE_HEX = "F5F7FA"

CN_FONT = "PingFang SC"
MONO_FONT = "Menlo"


# ---------- 底层工具 ----------
def _set_run_font(run, name=CN_FONT, size=None, bold=None, color=None, mono=False):
    font = run.font
    font.name = MONO_FONT if mono else name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    target = MONO_FONT if mono else name
    rfonts.set(qn("w:ascii"), target)
    rfonts.set(qn("w:hAnsi"), target)
    rfonts.set(qn("w:eastAsia"), CN_FONT)
    rfonts.set(qn("w:cs"), target)
    if size is not None:
        font.size = Pt(size)
    if bold is not None:
        font.bold = bold
    if color is not None:
        font.color.rgb = color


def _shade(el_pr, hex_color):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    el_pr.append(shd)


def _para_border(paragraph, edge="bottom", color=ACCENT_HEX, sz=12, space=4):
    ppr = paragraph._p.get_or_add_pPr()
    pbdr = ppr.find(qn("w:pBdr"))
    if pbdr is None:
        pbdr = OxmlElement("w:pBdr")
        ppr.append(pbdr)
    el = OxmlElement(f"w:{edge}")
    el.set(qn("w:val"), "single")
    el.set(qn("w:sz"), str(sz))
    el.set(qn("w:space"), str(space))
    el.set(qn("w:color"), color)
    pbdr.append(el)


def _field(paragraph, instr):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = instr
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    txt = OxmlElement("w:t")
    txt.text = "1"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr_el)
    run._r.append(fld_sep)
    run._r.append(txt)
    run._r.append(fld_end)
    return run


def _table_layout_fixed(table, widths_cm):
    tbl = table._tbl
    tbl_pr = tbl.tblPr
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tbl_pr.append(layout)
    # 总宽
    total = int(sum(widths_cm) * 567)
    tbl_w = OxmlElement("w:tblW")
    tbl_w.set(qn("w:w"), str(total))
    tbl_w.set(qn("w:type"), "dxa")
    tbl_pr.append(tbl_w)
    # 网格列宽
    grid = tbl.find(qn("w:tblGrid"))
    if grid is not None:
        for gc, w in zip(grid.findall(qn("w:gridCol")), widths_cm):
            gc.set(qn("w:w"), str(int(w * 567)))


def _table_borders(table, color=BORDER_HEX, header_dark=True):
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    spec = {
        "top": ("single", 10, TABLE_HEAD_HEX if header_dark else color),
        "left": ("nil", 0, color),
        "bottom": ("single", 10, TABLE_HEAD_HEX if header_dark else color),
        "right": ("nil", 0, color),
        "insideH": ("single", 4, color),
        "insideV": ("nil", 0, color),
    }
    for edge, (val, sz, col) in spec.items():
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), val)
        el.set(qn("w:sz"), str(sz))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), col)
        borders.append(el)
    tbl_pr.append(borders)


def _cell_margins(table, top=70, bottom=70, left=110, right=110):
    tbl_pr = table._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for edge, val in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:w"), str(val))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tbl_pr.append(mar)


def _no_split(row):
    tr_pr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:cantSplit")
    tr_pr.append(el)


# ---------- OOXML 子元素顺序修正 ----------
_NS = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
_ORDER = {
    "pPr": ["pStyle","keepNext","keepLines","pageBreakBefore","framePr","widowControl","numPr",
            "suppressLineNumbers","pBdr","shd","tabs","suppressAutoHyphens","kinsoku","wordWrap",
            "overflowPunct","topLinePunct","autoSpaceDE","autoSpaceDN","bidi","adjustRightInd",
            "snapToGrid","spacing","ind","contextualSpacing","mirrorIndents","suppressOverlap","jc",
            "textDirection","textAlignment","textboxTightWrap","outlineLvl","divId","cnfStyle",
            "rPr","sectPr","pPrChange"],
    "tblPr": ["tblStyle","tblpPr","tblOverlap","bidiVisual","tblStyleRowBandSize","tblStyleColBandSize",
              "tblW","jc","tblCellSpacing","tblInd","tblBorders","shd","tblLayout","tblCellMar",
              "tblLook","tblCaption","tblDescription"],
    "trPr": ["cnfStyle","divId","gridBefore","gridAfter","wBefore","wAfter","cantSplit","trHeight",
             "tblHeader","tblCellSpacing","jc","hidden","ins","del","trPrChange"],
    "tcPr": ["cnfStyle","tcW","gridSpan","hMerge","vMerge","tcBorders","shd","noWrap","tcMar",
             "textDirection","tcFitText","vAlign","hideMark","headers","cellIns","cellDel","cellMerge",
             "tcPrChange"],
}


def normalize_order(doc):
    """按 OOXML schema 顺序重排属性容器的子元素，避免 Word 报「内容有问题」。"""
    for tag, order in _ORDER.items():
        index = {name: i for i, name in enumerate(order)}
        for el in doc.element.body.iter(_NS + tag):
            children = list(el)
            if not children:
                continue
            children.sort(key=lambda c: index.get(c.tag.replace(_NS, ""), len(order)))
            for c in children:
                el.append(c)
    # 页眉页脚同样处理
    for section in doc.sections:
        for part in (section.header, section.footer):
            for tag, order in _ORDER.items():
                index = {name: i for i, name in enumerate(order)}
                for el in part._element.iter(_NS + tag):
                    children = list(el)
                    if not children:
                        continue
                    children.sort(key=lambda c: index.get(c.tag.replace(_NS, ""), len(order)))
                    for c in children:
                        el.append(c)


# ---------- 文档骨架 ----------
class Handbook:
    def __init__(self, footer_text=""):
        self.doc = Document()
        self.footer_text = footer_text
        self._setup_page()
        self._setup_styles()
        self._setup_header_footer()

    # -- 页面 --
    def _setup_page(self):
        s = self.doc.sections[0]
        s.page_width = Cm(21.0)
        s.page_height = Cm(29.7)
        s.top_margin = Cm(2.1)
        s.bottom_margin = Cm(1.9)
        s.left_margin = Cm(2.2)
        s.right_margin = Cm(2.2)
        s.header_distance = Cm(1.1)
        s.footer_distance = Cm(0.9)
        s.different_first_page_header_footer = True

    def _setup_styles(self):
        normal = self.doc.styles["Normal"]
        normal.font.name = CN_FONT
        normal.font.size = Pt(10.5)
        normal.font.color.rgb = INK
        normal.element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), CN_FONT)
        pf = normal.paragraph_format
        pf.line_spacing = 1.5
        pf.space_before = Pt(0)
        pf.space_after = Pt(3)

        specs = {
            "Heading 1": (17, True, INK, 20, 8),
            "Heading 2": (13, True, INK, 14, 4),
            "Heading 3": (11, True, INK, 10, 3),
        }
        for name, (size, bold, color, before, after) in specs.items():
            st = self.doc.styles[name]
            st.font.name = CN_FONT
            st.font.size = Pt(size)
            st.font.bold = bold
            st.font.color.rgb = color
            st.element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), CN_FONT)
            st.paragraph_format.space_before = Pt(before)
            st.paragraph_format.space_after = Pt(after)
            st.paragraph_format.keep_with_next = True
            st.paragraph_format.line_spacing = 1.25

    def _setup_header_footer(self):
        section = self.doc.sections[0]
        # 页眉
        hp = section.header.paragraphs[0]
        hp.text = ""
        run = hp.add_run(self.footer_text)
        _set_run_font(run, size=8, color=MUTED)
        hp.alignment = WD_ALIGN_PARAGRAPH.LEFT
        _para_border(hp, "bottom", BORDER_HEX, 4, 2)
        # 页脚：左标题 + 右页码
        fp = section.footer.paragraphs[0]
        fp.text = ""
        fp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        r2 = fp.add_run("— ")
        _set_run_font(r2, size=8, color=MUTED)
        fr = _field(fp, " PAGE ")
        _set_run_font(fr, size=8.5, color=MUTED)
        r3 = fp.add_run(" —")
        _set_run_font(r3, size=8, color=MUTED)

    # -- 文本 --
    def _add_inline(self, paragraph, text, size=10.5, color=INK, bold=False):
        """支持 **加粗** 与 `等宽` 两种行内标记。"""
        tokens = re.split(r"(\*\*.+?\*\*|`.+?`)", text)
        for token in tokens:
            if not token:
                continue
            if token.startswith("**") and token.endswith("**") and len(token) > 4:
                run = paragraph.add_run(token[2:-2])
                _set_run_font(run, size=size, bold=True, color=color)
            elif token.startswith("`") and token.endswith("`") and len(token) > 2:
                run = paragraph.add_run(token[1:-1])
                _set_run_font(run, size=size - 0.7, color=ACCENT, mono=True)
            else:
                run = paragraph.add_run(token)
                _set_run_font(run, size=size, bold=bold, color=color)

    def p(self, text, size=10.5, color=INK, space_after=5, align=None, indent=None):
        para = self.doc.add_paragraph()
        pf = para.paragraph_format
        pf.space_after = Pt(space_after)
        pf.line_spacing = Pt(17.4)
        if align == "center":
            para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        if indent:
            pf.left_indent = Cm(indent)
        self._add_inline(para, text, size=size, color=color)
        return para

    def lead(self, text):
        para = self.doc.add_paragraph()
        pf = para.paragraph_format
        pf.space_before = Pt(2)
        pf.space_after = Pt(8)
        pf.line_spacing = Pt(18.2)
        self._add_inline(para, text, size=10.5, color=MUTED)
        return para

    def h1(self, text, page_break=False):
        para = self.doc.add_paragraph(text, style="Heading 1")
        if page_break:
            para.paragraph_format.page_break_before = True
            para.paragraph_format.space_before = Pt(0)
        else:
            para.paragraph_format.space_before = Pt(30)
        para.paragraph_format.space_after = Pt(10)
        _para_border(para, "bottom", ACCENT_HEX, 12, 6)
        return para

    def h2(self, text):
        return self.doc.add_paragraph(text, style="Heading 2")

    def h3(self, text):
        return self.doc.add_paragraph(text, style="Heading 3")

    def bullets(self, items, level=1, size=10.5):
        for item in items:
            para = self.doc.add_paragraph()
            pf = para.paragraph_format
            pf.line_spacing = Pt(16.8)
            pf.space_after = Pt(2)
            if level == 1:
                pf.left_indent = Cm(0.75)
                pf.first_line_indent = Cm(-0.45)
                prefix = "•  "
            else:
                pf.left_indent = Cm(1.45)
                pf.first_line_indent = Cm(-0.45)
                prefix = "–  "
            run = para.add_run(prefix)
            _set_run_font(run, size=size, color=ACCENT if level == 1 else MUTED)
            self._add_inline(para, item, size=size)

    def steps(self, items, size=10.5):
        for idx, item in enumerate(items, 1):
            para = self.doc.add_paragraph()
            pf = para.paragraph_format
            pf.line_spacing = Pt(16.8)
            pf.space_after = Pt(3)
            pf.left_indent = Cm(1.0)
            pf.first_line_indent = Cm(-0.75)
            run = para.add_run(f"{idx}. ")
            _set_run_font(run, size=size, bold=True, color=ACCENT)
            self._add_inline(para, item, size=size)

    def caption(self, text):
        para = self.doc.add_paragraph()
        pf = para.paragraph_format
        pf.space_before = Pt(8)
        pf.space_after = Pt(3)
        pf.keep_with_next = True
        self._add_inline(para, text, size=8.5, color=MUTED)
        return para

    # -- 表格 --
    def table(self, headers, rows, widths, size=9, caption=None, first_col_bold=False,
              header_align="left"):
        if caption:
            self.caption(caption)
        n_cols = len(headers)
        table = self.doc.add_table(rows=1, cols=n_cols)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False
        _table_layout_fixed(table, widths)
        _table_borders(table)
        _cell_margins(table)

        # 表头
        head = table.rows[0]
        _no_split(head)
        tr_pr = head._tr.get_or_add_trPr()
        th = OxmlElement("w:tblHeader")
        tr_pr.append(th)
        for i, text in enumerate(headers):
            cell = head.cells[i]
            cell.width = Cm(widths[i])
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            _shade(cell._tc.get_or_add_tcPr(), TABLE_HEAD_HEX)
            para = cell.paragraphs[0]
            para.paragraph_format.space_after = Pt(0)
            para.paragraph_format.line_spacing = 1.2
            run = para.add_run(text)
            _set_run_font(run, size=size - 0.5, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF))

        for r_idx, row in enumerate(rows):
            tr = table.add_row()
            _no_split(tr)
            for c_idx, value in enumerate(row):
                cell = tr.cells[c_idx]
                cell.width = Cm(widths[c_idx])
                cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP
                if r_idx % 2 == 1:
                    _shade(cell._tc.get_or_add_tcPr(), ROW_ALT_HEX)
                chunks = str(value).split("\n")
                first = True
                for chunk in chunks:
                    para = cell.paragraphs[0] if first else cell.add_paragraph()
                    first = False
                    para.paragraph_format.space_after = Pt(0)
                    para.paragraph_format.line_spacing = Pt(12.6)
                    bold = first_col_bold and c_idx == 0
                    self._add_inline(para, chunk, size=size, bold=bold)
        if len(rows) <= 12:
            for tr in table.rows[:-1]:
                for cell in tr.cells:
                    for para in cell.paragraphs:
                        para.paragraph_format.keep_with_next = True

        # 表后留白
        spacer = self.doc.add_paragraph()
        spacer.paragraph_format.space_after = Pt(0)
        spacer.paragraph_format.line_spacing = 1
        spacer.paragraph_format.space_before = Pt(0)
        for r in spacer.runs:
            r.font.size = Pt(4)
        spacer.add_run("").font.size = Pt(4)
        return table

    # -- 代码块 --
    def code(self, text, caption=None, size=8.5):
        if caption:
            self.caption(caption)
        table = self.doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False
        _table_layout_fixed(table, [16.4])
        tbl_pr = table._tbl.tblPr
        borders = OxmlElement("w:tblBorders")
        for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
            el = OxmlElement(f"w:{edge}")
            el.set(qn("w:val"), "single")
            el.set(qn("w:sz"), "4")
            el.set(qn("w:space"), "0")
            el.set(qn("w:color"), BORDER_HEX)
            borders.append(el)
        tbl_pr.append(borders)
        _cell_margins(table, 110, 110, 150, 150)

        cell = table.rows[0].cells[0]
        cell.width = Cm(16.4)
        _shade(cell._tc.get_or_add_tcPr(), CODE_HEX)
        lines = text.strip("\n").split("\n")
        if len(lines) <= 22:
            _no_split(table.rows[0])
        para = cell.paragraphs[0]
        for i, line in enumerate(lines):
            if i > 0:
                para = cell.add_paragraph()
            para.paragraph_format.space_after = Pt(0)
            para.paragraph_format.space_before = Pt(0)
            para.paragraph_format.line_spacing = Pt(12.2)
            run = para.add_run(line if line else " ")
            _set_run_font(run, size=size, color=RGBColor(0x24, 0x29, 0x2E), mono=True)
        spacer = self.doc.add_paragraph()
        spacer.paragraph_format.space_before = Pt(0)
        spacer.paragraph_format.space_after = Pt(6)
        spacer.paragraph_format.line_spacing = 1
        return table

    # -- 提示框 --
    def callout(self, title, body, kind="accent"):
        palette = {
            "accent": (ACCENT_HEX, ACCENT_TINT, ACCENT),
            "note": (NOTE_HEX, NOTE_TINT, RGBColor(0x1F, 0x4E, 0x79)),
            "ok": (OK_HEX, OK_TINT, RGBColor(0x1E, 0x6B, 0x3A)),
        }
        border_hex, tint_hex, title_color = palette[kind]

        table = self.doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.autofit = False
        _table_layout_fixed(table, [16.4])
        tbl_pr = table._tbl.tblPr
        borders = OxmlElement("w:tblBorders")
        for edge in ("top", "bottom", "right", "insideH", "insideV"):
            el = OxmlElement(f"w:{edge}")
            el.set(qn("w:val"), "nil")
            el.set(qn("w:sz"), "0")
            el.set(qn("w:space"), "0")
            el.set(qn("w:color"), "auto")
            borders.append(el)
        left = OxmlElement("w:left")
        left.set(qn("w:val"), "single")
        left.set(qn("w:sz"), "24")
        left.set(qn("w:space"), "0")
        left.set(qn("w:color"), border_hex)
        borders.append(left)
        tbl_pr.append(borders)
        _cell_margins(table, 130, 130, 220, 180)

        _no_split(table.rows[0])
        cell = table.rows[0].cells[0]
        cell.width = Cm(16.4)
        _shade(cell._tc.get_or_add_tcPr(), tint_hex)

        para = cell.paragraphs[0]
        para.paragraph_format.space_after = Pt(2)
        para.paragraph_format.line_spacing = 1.3
        run = para.add_run(title)
        _set_run_font(run, size=9.5, bold=True, color=title_color)

        items = body if isinstance(body, (list, tuple)) else [body]
        for item in items:
            bp = cell.add_paragraph()
            bp.paragraph_format.space_after = Pt(2)
            bp.paragraph_format.line_spacing = Pt(16.2)
            if len(items) > 1:
                bp.paragraph_format.left_indent = Cm(0.45)
                bp.paragraph_format.first_line_indent = Cm(-0.35)
                r = bp.add_run("·  ")
                _set_run_font(r, size=9.5, color=title_color, bold=True)
            self._add_inline(bp, item, size=9.5, color=INK)

        spacer = self.doc.add_paragraph()
        spacer.paragraph_format.space_before = Pt(0)
        spacer.paragraph_format.space_after = Pt(6)
        spacer.paragraph_format.line_spacing = 1
        return table

    def quote(self, text):
        para = self.doc.add_paragraph()
        pf = para.paragraph_format
        pf.left_indent = Cm(0.6)
        pf.space_before = Pt(2)
        pf.space_after = Pt(6)
        pf.line_spacing = 1.45
        _para_border(para, "left", BORDER_HEX, 18, 10)
        self._add_inline(para, text, size=10, color=MUTED)
        return para

    def pagebreak(self):
        self.doc.add_page_break()

    def save(self, path):
        normalize_order(self.doc)
        self.doc.save(path)
