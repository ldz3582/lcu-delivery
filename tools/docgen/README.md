# 《聊大外卖小程序_开发行动手册.docx》生成脚本

把手册正文写成 Python 模块（`content_a/b/c.py`），由 `docx_kit.py` 渲染成 Word，
改内容后重跑即可，不用手工调格式。

## 用法

```bash
python -m pip install -r requirements.txt
python build.py
# 输出覆盖 docs/design/聊大外卖小程序_开发行动手册.docx
```

## 文件

| 文件 | 作用 |
| --- | --- |
| `build.py` | 入口，按顺序拼装各章并保存 |
| `docx_kit.py` | 排版工具：字体、表格、边框、页脚、目录域 |
| `content_a.py` | 封面 + 概览 + 地图页 + 第 1–2 章 |
| `content_b.py` | 第 3–4 章（架构 / 接口与数据） |
| `content_c.py` | 第 5–9 章 + 附录 |
| `dump_docx.py` | 把已有 docx 的段落/表格导成文本，便于查错 |
