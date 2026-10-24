# 接口清单

共 123 个接口，前缀 `/api`，统一响应 `{code,msg,data}`，`code=0` 成功。
鉴权：`Authorization: Bearer <token>`；未登录返回 401，越权返回 403。

| 方法 | 路径 | 权限 | 模块 |
| --- | --- | --- | --- |
| `GET` | `/api/addresses` | 登录用户 | Address |
| `POST` | `/api/addresses` | 登录用户 | Address |
| `POST` | `/api/addresses/parse` | 登录用户 | Address |
| `DELETE` | `/api/addresses/{id}` | 登录用户 | Address |
| `PUT` | `/api/addresses/{id}` | 登录用户 | Address |
| `GET` | `/api/admin/after-sales` | 管理员 | AfterSale |
| `GET` | `/api/admin/after-sales/{id}` | 管理员 | AfterSale |
| `POST` | `/api/admin/after-sales/{id}/judge` | 管理员 | AfterSale |
| `GET` | `/api/admin/configs` | 管理员 | Admin |
| `PUT` | `/api/admin/configs` | 管理员 | Admin |
| `GET` | `/api/admin/coupons` | 管理员 | AdminCoupon |
| `POST` | `/api/admin/coupons` | 管理员 | AdminCoupon |
| `PUT` | `/api/admin/coupons/{id}/status` | 管理员 | AdminCoupon |
| `GET` | `/api/admin/fence` | 管理员 | Admin |
| `PUT` | `/api/admin/fence` | 管理员 | Admin |
| `GET` | `/api/admin/incidents` | 管理员 | AdminIncident |
| `POST` | `/api/admin/incidents/{id}/handle` | 管理员 | AdminIncident |
| `POST` | `/api/admin/login` | 公开 | Admin |
| `GET` | `/api/admin/notify-logs` | 管理员 | Notify |
| `GET` | `/api/admin/orders` | 管理员 | Admin |
| `GET` | `/api/admin/overview` | 管理员 | Admin |
| `GET` | `/api/admin/penalties` | 管理员 | AdminPenalty |
| `GET` | `/api/admin/reviews` | 管理员 | Review |
| `PUT` | `/api/admin/reviews/{id}/status` | 管理员 | Review |
| `GET` | `/api/admin/riders` | 管理员 | Admin |
| `POST` | `/api/admin/riders/{id}/audit` | 管理员 | Admin |
| `POST` | `/api/admin/riders/{riderId}/penalty` | 管理员 | AdminPenalty |
| `GET` | `/api/admin/settlements` | 管理员 | Admin |
| `POST` | `/api/admin/settlements/{id}/settle` | 管理员 | Admin |
| `GET` | `/api/admin/shops` | 管理员 | Admin |
| `POST` | `/api/admin/shops/{id}/audit` | 管理员 | Admin |
| `GET` | `/api/admin/users` | 管理员 | Admin |
| `GET` | `/api/admin/withdrawals` | 管理员 | AdminWithdraw |
| `POST` | `/api/admin/withdrawals/{id}/audit` | 管理员 | AdminWithdraw |
| `POST` | `/api/after-sales` | 登录用户 | AfterSale |
| `GET` | `/api/after-sales/mine` | 登录用户 | AfterSale |
| `GET` | `/api/after-sales/{id}` | 登录用户 | AfterSale |
| `POST` | `/api/after-sales/{id}/cancel` | 登录用户 | AfterSale |
| `POST` | `/api/auth/login` | 公开 | Auth |
| `GET` | `/api/auth/mock-accounts` | 公开 | Auth |
| `POST` | `/api/auth/mock-login` | 公开 | Auth |
| `POST` | `/api/auth/stu-login` | 公开 | Auth |
| `POST` | `/api/cart/calculate` | 登录用户 | Cart |
| `GET` | `/api/categories` | 公开 | Shop |
| `GET` | `/api/coupons/available` | 登录用户 | Coupon |
| `GET` | `/api/coupons/mine` | 登录用户 | Coupon |
| `POST` | `/api/coupons/{id}/claim` | 登录用户 | Coupon |
| `GET` | `/api/favorites` | 登录用户 | Favorite |
| `GET` | `/api/favorites/check` | 登录用户 | Favorite |
| `DELETE` | `/api/favorites/{shopId}` | 登录用户 | Favorite |
| `POST` | `/api/favorites/{shopId}/toggle` | 登录用户 | Favorite |
| `GET` | `/api/fence` | 公开 | Fence |
| `GET` | `/api/identity/config` | 公开 | Identity |
| `GET` | `/api/identity/status` | 登录用户 | Identity |
| `POST` | `/api/identity/verify` | 登录用户 | Identity |
| `GET` | `/api/merchant/after-sales` | 商家 | AfterSale |
| `POST` | `/api/merchant/after-sales/{id}/handle` | 商家 | AfterSale |
| `POST` | `/api/merchant/apply` | 商家 | Merchant |
| `GET` | `/api/merchant/coupons` | 商家 | MerchantCoupon |
| `POST` | `/api/merchant/coupons` | 商家 | MerchantCoupon |
| `PUT` | `/api/merchant/coupons/{id}/status` | 商家 | MerchantCoupon |
| `GET` | `/api/merchant/orders` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/accept` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/ready` | 商家 | Merchant |
| `POST` | `/api/merchant/orders/{id}/reject` | 商家 | Merchant |
| `GET` | `/api/merchant/products` | 商家 | Merchant |
| `POST` | `/api/merchant/products` | 商家 | Merchant |
| `PUT` | `/api/merchant/products/{id}` | 商家 | Merchant |
| `GET` | `/api/merchant/products/{id}/skus` | 商家 | Merchant |
| `POST` | `/api/merchant/products/{id}/skus` | 商家 | Merchant |
| `POST` | `/api/merchant/reviews/{id}/reply` | 商家 | Review |
| `GET` | `/api/merchant/shop` | 商家 | Merchant |
| `PUT` | `/api/merchant/shop` | 商家 | Merchant |
| `DELETE` | `/api/merchant/skus/{skuId}` | 商家 | Merchant |
| `PUT` | `/api/merchant/skus/{skuId}` | 商家 | Merchant |
| `GET` | `/api/merchant/statements` | 商家 | MerchantFinance |
| `GET` | `/api/merchant/status` | 商家 | Merchant |
| `GET` | `/api/merchant/summary` | 商家 | MerchantFinance |
| `GET` | `/api/merchant/wallet` | 商家 | MerchantFinance |
| `GET` | `/api/merchant/withdrawals` | 商家 | MerchantFinance |
| `POST` | `/api/merchant/withdrawals` | 商家 | MerchantFinance |
| `GET` | `/api/notify/templates` | 登录用户 | Notify |
| `GET` | `/api/orders` | 登录用户 | Order |
| `POST` | `/api/orders` | 登录用户 | Order |
| `GET` | `/api/orders/{id}` | 登录用户 | Order |
| `POST` | `/api/orders/{id}/cancel` | 登录用户 | Order |
| `POST` | `/api/orders/{id}/confirm` | 登录用户 | Order |
| `GET` | `/api/orders/{id}/track` | 登录用户 | Order |
| `POST` | `/api/pay/mock-pay` | 登录用户 | Pay |
| `POST` | `/api/pay/notify` | 公开 | Pay |
| `POST` | `/api/pay/prepay` | 登录用户 | Pay |
| `POST` | `/api/pay/refund` | 登录用户 | Pay |
| `POST` | `/api/reviews` | 登录用户 | Review |
| `GET` | `/api/reviews/mine` | 登录用户 | Review |
| `GET` | `/api/reviews/order/{orderId}` | 登录用户 | Review |
| `POST` | `/api/rider/apply` | 骑手 | Delivery |
| `GET` | `/api/rider/dispatched` | 骑手 | Delivery |
| `POST` | `/api/rider/dispatched/{id}/accept` | 骑手 | Delivery |
| `POST` | `/api/rider/dispatched/{id}/reject` | 骑手 | Delivery |
| `GET` | `/api/rider/incidents` | 骑手 | Delivery |
| `POST` | `/api/rider/location` | 骑手 | Delivery |
| `GET` | `/api/rider/orders` | 骑手 | Delivery |
| `POST` | `/api/rider/orders/{id}/deliver` | 骑手 | Delivery |
| `POST` | `/api/rider/orders/{id}/grab` | 骑手 | Delivery |
| `POST` | `/api/rider/orders/{id}/incident` | 骑手 | Delivery |
| `POST` | `/api/rider/orders/{id}/pickup` | 骑手 | Delivery |
| `GET` | `/api/rider/penalties` | 骑手 | RiderFinance |
| `GET` | `/api/rider/pool` | 骑手 | Delivery |
| `GET` | `/api/rider/profile` | 骑手 | Delivery |
| `GET` | `/api/rider/settlements` | 骑手 | RiderFinance |
| `GET` | `/api/rider/status` | 骑手 | Delivery |
| `GET` | `/api/rider/summary` | 骑手 | RiderFinance |
| `GET` | `/api/rider/wallet` | 骑手 | RiderFinance |
| `GET` | `/api/rider/withdrawals` | 骑手 | RiderFinance |
| `POST` | `/api/rider/withdrawals` | 骑手 | RiderFinance |
| `GET` | `/api/shops` | 公开 | Shop |
| `GET` | `/api/shops/{id}` | 公开 | Shop |
| `GET` | `/api/shops/{id}/products` | 公开 | Shop |
| `GET` | `/api/shops/{shopId}/reviews` | 公开 | Review |
| `POST` | `/api/upload` | 登录用户 | Upload |
| `POST` | `/api/user/phone` | 登录用户 | User |
| `GET` | `/api/user/profile` | 登录用户 | User |
| `PUT` | `/api/user/profile` | 登录用户 | User |

## 错误码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 401 | 未登录 / 登录过期 |
| 403 | 无权限 |
| 404 | 资源不存在（含不属于本人的订单/工单） |
| 1001 | 参数不合法 |
| 2000 | 订单/工单状态不允许该操作 |
| 2001 | 超出校园配送范围 |
| 2002 | 库存不足 |
| 2003 | 商家休息中 |
| 2004 | 未达起送价 |
| 2005 | 校内身份未核验 |
| 4001 | 支付失败 |

> 由 `docs/gen_api.py` 生成；新增接口后重跑即可。
