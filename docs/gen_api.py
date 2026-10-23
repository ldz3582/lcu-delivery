# 重新生成 docs/API.md：python3 docs/gen_api.py
import os, re
root = "server/src/main/java/com/lcu/delivery/modules"
PUBLIC = {"/api/auth/login","/api/auth/mock-login","/api/admin/login","/api/categories",
          "/api/pay/notify","/api/health"}
rows = []
for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.endswith("Controller.java"):
            continue
        path = os.path.join(dirpath, f)
        src = open(path, encoding="utf-8").read()
        m0 = re.search(r'@RequestMapping\("([^"]+)"\)', src)
        base = m0.group(1) if m0 else ""
        for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping\((?:value\s*=\s*)?"([^"]*)"\)', src):
            full = base + m.group(2)
            if full in PUBLIC or full.startswith("/api/shops"):
                auth = "公开"
            elif "admin" in path:
                auth = "管理员"
            elif "merchant" in path:
                auth = "商家"
            elif "rider" in path:
                auth = "骑手"
            else:
                auth = "登录用户"
            rows.append((full, m.group(1).upper(), auth, f.replace("Controller.java", "")))
rows.sort(key=lambda r: (r[0], r[1]))
lines = ["# 接口清单", "",
         f"共 {len(rows)} 个接口，前缀 `/api`，统一响应 `{{code,msg,data}}`，`code=0` 成功。",
         "鉴权：`Authorization: Bearer <token>`；未登录返回 401，越权返回 403。", "",
         "| 方法 | 路径 | 权限 | 模块 |", "| --- | --- | --- | --- |"]
for p, m, a, mod in rows:
    lines.append(f"| `{m}` | `{p}` | {a} | {mod} |")
lines += ["", "## 错误码", "", "| code | 含义 |", "| --- | --- |",
          "| 0 | 成功 |", "| 401 | 未登录 / 登录过期 |", "| 403 | 无权限 |",
          "| 1001 | 参数不合法 |", "| 2001 | 超出校园配送范围 |", "| 2002 | 库存不足 |",
          "| 2003 | 商家休息中 |", "| 2004 | 未达起送价 |", "| 4001 | 支付失败 |", "",
          "> 由 `docs/gen_api.py` 生成；新增接口后重跑即可。"]
open("docs/API.md", "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("generated", len(rows), "endpoints")
