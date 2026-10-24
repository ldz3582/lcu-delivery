import sys
from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

path = "聊大外卖小程序设计方案(1).docx"
doc = Document(path)

def iter_block_items(parent):
    from docx.oxml.ns import qn
    body = parent.element.body
    for child in body.iterchildren():
        if child.tag == qn('w:p'):
            yield Paragraph(child, parent)
        elif child.tag == qn('w:tbl'):
            yield Table(child, parent)

out = []
for block in iter_block_items(doc):
    if isinstance(block, Paragraph):
        t = block.text.strip()
        if t:
            out.append(f"[{block.style.name}] {t}")
    else:
        out.append("=== TABLE ===")
        for row in block.rows:
            cells = []
            seen = set()
            for c in row.cells:
                if id(c._tc) in seen: continue
                seen.add(id(c._tc))
                cells.append(c.text.strip().replace("\n"," / "))
            out.append(" | ".join(cells))
        out.append("=== END TABLE ===")

text = "\n".join(out)
open("work/doc_text.txt","w").write(text)
print("chars:", len(text))
print("paras:", len(doc.paragraphs), "tables:", len(doc.tables))
