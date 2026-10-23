# 接口清单

共 50 个接口，前缀 `/api`，统一响应 `{code,msg,data}`，`code=0` 成功。
鉴权：`Authorization: Bearer <token>`；未登录返回 401，越权返回 403。

| 方法 | 路径 | 权限 | 模块 |
| --- | --- | --- | --- |
| `POST` | `/api/addresses/parse` | 登录用户 | Address |
| `DELETE` | `/api/addresses/{id}` | 登录用户 | Address |
| `PUT` | `/api/addresses/{id}` | 登录用户 | Address |
| `GET` | `/api/admin/configs` | 管理员 | Admin |
| `PUT` | `/api/admin/configs` | 管理员 | Admin |
| `GET` | `/api/admin/fence` | 管理员 | Admin |
| `PUT` | `/api/admin/fence` | 管理员 | Admin |
| `POST` | `/api/admin/login` | 公开 | Admin |
| `GET` | `/api/admin/orders` | 管理员 | Admin |
| `GET` | `/api/admin/overview` | 管理员 | Admin |
| `GET` | `/api/admin/riders` | 管理员 | Admin |
| `POST` | `/api/admin/riders/{id}/audit` | 管理员 | Admin |
| `GET` | `/api/admin/settlements` | 管理员 | Admin |
| `GET` | `/api/admin/shops` | 管理员 | Admin |
| `POST` | `/api/admin/shops/{id}/audit` | 管理员 | Admin |
| `POST` | `/api/auth/login` | 公开 | Auth |
| `POST` | `/api/auth/mock-login` | 公开 | Auth |
| `POST` | `/api/cart/calculate` | 登录用户 | Cart |
| `GET` | `/api/categories` | 公开 | Shop |
| `POST` | `/api/merchant/apply` | 商家 | Merchant |
| `GET` | `/api/merchant/orders` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/accept` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/ready` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/reject` | 商家 | Merchant |
| `GET` | `/api/merchant/products` | 商家 | Merchant |
| `POST` | `/api/merchant/products` | 商家 | Merchant |
| `PUT` | `/api/merchant/products/{id}` | 商家 | Merchant |
| `GET` | `/api/merchant/shop` | 商家 | Merchant |
| `PUT` | `/api/merchant/shop` | 商家 | Merchant |
| `GET` | `/api/orders/{id}` | 登录用户 | Order |
| `POST` | `/api/orders/{id}/cancel` | 登录用户 | Order |
| `POST` | `/api/orders/{id}/confirm` | 登录用户 | Order |
| `POST` | `/api/pay/mock-pay` | 登录用户 | Pay |
| `POST` | `/api/pay/notify` | 公开 | Pay |
| `POST` | `/api/pay/prepay` | 登录用户 | Pay |
| `POST` | `/api/pay/refund` | 登录用户 | Pay |
| `POST` | `/api/rider/apply` | 登录用户 | Delivery |
| `POST` | `/api/rider/location` | 登录用户 | Delivery |
| `GET` | `/api/rider/orders` | 登录用户 | Delivery |
| `POST` | `/api/rider/orders/{id}/deliver` | 登录用户 | Delivery |
| `POST` | `/api/rider/orders/{id}/grab` | 登录用户 | Delivery |
| `POST` | `/api/rider/orders/{id}/pickup` | 登录用户 | Delivery |
| `GET` | `/api/rider/pool` | 登录用户 | Delivery |
| `GET` | `/api/rider/profile` | 登录用户 | Delivery |
| `GET` | `/api/shops` | 公开 | Shop |
| `GET` | `/api/shops/{id}` | 公开 | Shop |
| `GET` | `/api/shops/{id}/products` | 公开 | Shop |
| `POST` | `/api/user/phone` | 登录用户 | User |
| `GET` | `/api/user/profile` | 登录用户 | User |
| `PUT` | `/api/user/profile` | 登录用户 | User |

## 错误码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 401 | 未登录 / 登录过期 |
| 403 | 无权限 |
| 1001 | 参数不合法 |
| 2001 | 超出校园配送范围 |
| 2002 | 库存不足 |
| 2003 | 商家休息中 |
| 2004 | 未达起送价 |
| 4001 | 支付失败 |

> 由 `docs/gen_api.py` 从 Controller 注解生成；新增接口后重跑即可。
