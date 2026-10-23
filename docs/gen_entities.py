# -*- coding: utf-8 -*-
"""从字段定义生成 MyBatis-Plus 实体与 Mapper，保证与 schema.sql 一致。"""
import os

BASE = "server/src/main/java/com/lcu/delivery/modules"

ENTITIES = {
    "user/entity/User": ("user", "用户", [
        ("Long", "id", None),
        ("String", "openid", None),
        ("String", "unionid", None),
        ("String", "nickname", None),
        ("String", "avatar", None),
        ("String", "phoneMask", None),
        ("String", "role", "USER / MERCHANT / RIDER / ADMIN"),
        ("Integer", "status", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
        ("LocalDateTime", "deletedAt", None),
    ]),
    "user/entity/UserAddress": ("user_address", "收货地址", [
        ("Long", "id", None),
        ("Long", "userId", None),
        ("Integer", "campus", "1 东校区 2 西校区"),
        ("String", "detail", None),
        ("String", "building", None),
        ("String", "floor", None),
        ("String", "room", None),
        ("String", "remark", None),
        ("BigDecimal", "lng", None),
        ("BigDecimal", "lat", None),
        ("Integer", "inFence", None),
        ("Integer", "parseSource", "1 手动 2 正则 3 地图 4 大模型"),
        ("Integer", "isDefault", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
        ("LocalDateTime", "deletedAt", None),
    ]),
    "shop/entity/Shop": ("shop", "店铺", [
        ("Long", "id", None),
        ("Long", "ownerUserId", None),
        ("String", "name", None),
        ("String", "logo", None),
        ("Long", "categoryId", None),
        ("Integer", "campus", None),
        ("String", "address", None),
        ("BigDecimal", "lng", None),
        ("BigDecimal", "lat", None),
        ("String", "contactPhone", None),
        ("Integer", "bizStatus", "0 休息 1 营业"),
        ("String", "openTime", None),
        ("Integer", "minAmount", None),
        ("Integer", "packFee", None),
        ("Integer", "deliveryFee", None),
        ("BigDecimal", "commissionRate", None),
        ("BigDecimal", "score", None),
        ("Integer", "monthSales", None),
        ("Integer", "auditStatus", "0 待审 1 通过 2 驳回 3 冻结"),
        ("String", "licenseUrl", None),
        ("String", "permitUrl", None),
        ("Integer", "settleStatus", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
        ("LocalDateTime", "deletedAt", None),
    ]),
    "shop/entity/Product": ("product", "商品", [
        ("Long", "id", None),
        ("Long", "shopId", None),
        ("Long", "categoryId", None),
        ("String", "name", None),
        ("String", "image", None),
        ("String", "description", None),
        ("Integer", "price", "单位：分"),
        ("Integer", "originPrice", None),
        ("Integer", "stock", "-1 表示不限"),
        ("Integer", "sales", None),
        ("Integer", "hasSku", None),
        ("Integer", "status", "1 上架 0 下架"),
        ("Integer", "sort", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
        ("LocalDateTime", "deletedAt", None),
    ]),
    "order/entity/Order": ("orders", "订单", [
        ("Long", "id", None),
        ("String", "orderNo", None),
        ("Long", "userId", None),
        ("Long", "shopId", None),
        ("Long", "riderId", None),
        ("Integer", "status", "0 待支付 10 待接单 20 待出餐 30 待取餐 40 配送中 50 已完成 60 已取消 70 售后中 80 已退款"),
        ("Integer", "campus", None),
        ("String", "addressJson", None),
        ("BigDecimal", "lng", None),
        ("BigDecimal", "lat", None),
        ("Integer", "goodsAmount", None),
        ("Integer", "packFee", None),
        ("Integer", "deliveryFee", None),
        ("Integer", "discountAmount", None),
        ("Integer", "payAmount", None),
        ("Integer", "commission", None),
        ("Integer", "riderIncome", None),
        ("Integer", "shopIncome", None),
        ("Long", "couponId", None),
        ("String", "remark", None),
        ("LocalDateTime", "expectTime", None),
        ("LocalDateTime", "payExpireAt", None),
        ("LocalDateTime", "paidAt", None),
        ("LocalDateTime", "acceptedAt", None),
        ("LocalDateTime", "readyAt", None),
        ("LocalDateTime", "pickedAt", None),
        ("LocalDateTime", "deliveredAt", None),
        ("LocalDateTime", "finishedAt", None),
        ("String", "cancelReason", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
    "order/entity/OrderItem": ("order_item", "订单明细", [
        ("Long", "id", None),
        ("Long", "orderId", None),
        ("Long", "productId", None),
        ("Long", "skuId", None),
        ("String", "name", None),
        ("String", "image", None),
        ("Integer", "price", None),
        ("Integer", "qty", None),
        ("Integer", "subtotal", None),
    ]),
    "order/entity/OrderStatusLog": ("order_status_log", "订单状态流水", [
        ("Long", "id", None),
        ("Long", "orderId", None),
        ("Integer", "fromStatus", None),
        ("Integer", "toStatus", None),
        ("Integer", "operatorType", "1 用户 2 商家 3 骑手 4 系统 5 管理员"),
        ("Long", "operatorId", None),
        ("String", "remark", None),
        ("LocalDateTime", "createdAt", None),
    ]),
    "pay/entity/Payment": ("payment", "支付单", [
        ("Long", "id", None),
        ("Long", "orderId", None),
        ("String", "outTradeNo", None),
        ("String", "transactionId", None),
        ("Integer", "amount", None),
        ("Integer", "status", "0 待支付 1 成功 2 失败 3 已关闭"),
        ("String", "prepayId", None),
        ("Integer", "profitSharing", None),
        ("LocalDateTime", "paidAt", None),
        ("String", "raw", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
    "delivery/entity/Rider": ("rider", "骑手", [
        ("Long", "id", None),
        ("Long", "userId", None),
        ("String", "realNameEnc", None),
        ("String", "idCardEnc", None),
        ("String", "stuNoEnc", None),
        ("String", "phoneMask", None),
        ("Integer", "auditStatus", "0 待审 1 通过 2 驳回"),
        ("Integer", "workStatus", "0 离线 1 接单中 2 忙碌"),
        ("Integer", "deposit", None),
        ("Integer", "maxConcurrent", None),
        ("BigDecimal", "score", None),
        ("Integer", "totalOrders", None),
        ("Integer", "timeoutOrders", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
    "delivery/entity/DeliveryTask": ("delivery_task", "配送任务", [
        ("Long", "id", None),
        ("Long", "orderId", None),
        ("Long", "riderId", None),
        ("Integer", "assignType", "1 抢单 2 派单"),
        ("Integer", "status", "0 待接 1 已接 2 已取餐 3 已送达 4 已取消"),
        ("String", "pickupPhoto", None),
        ("String", "deliverPhoto", None),
        ("LocalDateTime", "grabAt", None),
        ("LocalDateTime", "pickupAt", None),
        ("LocalDateTime", "deliveredAt", None),
        ("Integer", "timeoutFlag", None),
        ("LocalDateTime", "createdAt", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
    "delivery/entity/RiderLocation": ("rider_location", "骑手轨迹", [
        ("Long", "id", None),
        ("Long", "riderId", None),
        ("Long", "orderId", None),
        ("BigDecimal", "lng", None),
        ("BigDecimal", "lat", None),
        ("Integer", "accuracy", None),
        ("BigDecimal", "speed", None),
        ("LocalDateTime", "reportedAt", None),
    ]),
    "settle/entity/Settlement": ("settlement", "结算单", [
        ("Long", "id", None),
        ("Long", "orderId", None),
        ("Long", "shopId", None),
        ("Long", "riderId", None),
        ("Integer", "goodsAmount", None),
        ("Integer", "commission", None),
        ("Integer", "shopIncome", None),
        ("Integer", "riderIncome", None),
        ("Integer", "status", "0 待结算 1 已结算 2 已撤销"),
        ("LocalDateTime", "settleAt", None),
        ("LocalDateTime", "createdAt", None),
    ]),
    "settle/entity/Wallet": ("wallet", "钱包", [
        ("Long", "id", None),
        ("Integer", "ownerType", "2 商家 3 骑手"),
        ("Long", "ownerId", None),
        ("Integer", "balance", None),
        ("Integer", "frozen", None),
        ("Integer", "version", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
    "settle/entity/WalletFlow": ("wallet_flow", "资金流水", [
        ("Long", "id", None),
        ("Long", "walletId", None),
        ("String", "bizType", None),
        ("String", "bizNo", None),
        ("Integer", "direction", "1 入账 -1 出账"),
        ("Integer", "amount", None),
        ("Integer", "balanceAfter", None),
        ("String", "remark", None),
        ("LocalDateTime", "createdAt", None),
    ]),
    "admin/entity/AdminUser": ("admin_user", "后台账号", [
        ("Long", "id", None),
        ("String", "username", None),
        ("String", "passwordHash", None),
        ("String", "realName", None),
        ("String", "role", None),
        ("String", "phoneMask", None),
        ("Integer", "status", None),
        ("LocalDateTime", "lastLoginAt", None),
        ("LocalDateTime", "createdAt", None),
    ]),
    "admin/entity/SysConfig": ("sys_config", "平台配置", [
        ("Long", "id", None),
        ("String", "configKey", None),
        ("String", "configValue", None),
        ("String", "valueType", None),
        ("String", "groupKey", None),
        ("String", "remark", None),
        ("Long", "updatedBy", None),
        ("LocalDateTime", "updatedAt", None),
    ]),
}

HEADER = "package com.lcu.delivery.modules.{pkg}.entity;\n\n"


def cap(name):
    return name[0].upper() + name[1:]


def gen_entity(path, table, comment, fields):
    pkg = path.split("/")[0]
    cls = path.split("/")[-1]
    lines = [
        f"package com.lcu.delivery.modules.{pkg}.entity;",
        "",
        "import com.baomidou.mybatisplus.annotation.IdType;",
        "import com.baomidou.mybatisplus.annotation.TableId;",
        "import com.baomidou.mybatisplus.annotation.TableName;",
        "",
    ]
    if "LocalDateTime" in {t for t, _, _ in fields}:
        lines.append("import java.time.LocalDateTime;")
    if "BigDecimal" in {t for t, _, _ in fields}:
        lines.append("import java.math.BigDecimal;")
    lines += [
        "",
        f"/** {comment}（表 {table}） */",
        f'@TableName("{table}")',
        f"public class {cls} {{",
        "",
    ]
    for typ, name, cmt in fields:
        if cmt:
            lines.append(f"    /** {cmt} */")
        lines.append(f"    private {typ} {name};")
    lines.append("")
    for typ, name, _ in fields:
        g = cap(name)
        lines.append(f"    public {typ} get{g}() {{")
        lines.append(f"        return {name};")
        lines.append("    }")
        lines.append("")
        lines.append(f"    public void set{g}({typ} {name}) {{")
        lines.append(f"        this.{name} = {name};")
        lines.append("    }")
        lines.append("")
    if fields[0][1] == "id":
        lines.insert(lines.index(f"    private {fields[0][0]} id;") - 0, "    @TableId(type = IdType.AUTO)")
    lines.append("}")
    out = os.path.join(BASE, path + ".java")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def gen_mapper(path, table, comment):
    pkg = path.split("/")[0]
    cls = path.split("/")[-1]
    lines = [
        f"package com.lcu.delivery.modules.{pkg}.mapper;",
        "",
        "import com.baomidou.mybatisplus.core.mapper.BaseMapper;",
        f"import com.lcu.delivery.modules.{pkg}.entity.{cls};",
        "import org.apache.ibatis.annotations.Mapper;",
        "",
        f"/** {comment}（表 {table}） */",
        "@Mapper",
        f"public interface {cls}Mapper extends BaseMapper<{cls}> {{",
        "}",
    ]
    out = os.path.join(BASE, f"{pkg}/mapper/{cls}Mapper.java")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


for path, (table, comment, fields) in ENTITIES.items():
    gen_entity(path, table, comment, fields)
    gen_mapper(path.split("entity/")[0] + "entity/" + path.split("/")[-1], table, comment)

print("entities:", len(ENTITIES))
