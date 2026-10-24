package com.lcu.delivery.modules.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.favorite.entity.Favorite;
import com.lcu.delivery.modules.favorite.mapper.FavoriteMapper;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ShopMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 收藏店铺：切换式收藏，列表按收藏时间倒序。 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;

    public FavoriteService(FavoriteMapper favoriteMapper, ShopMapper shopMapper, ShopService shopService) {
        this.favoriteMapper = favoriteMapper;
        this.shopMapper = shopMapper;
        this.shopService = shopService;
    }

    /** 收藏/取消收藏，返回操作后的状态。 */
    public boolean toggle(Long userId, Long shopId) {
        Shop shop = shopService.require(shopId);
        Favorite exists = favoriteMapper.selectOne(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getShopId, shopId));
        if (exists != null) {
            favoriteMapper.deleteById(exists.getId());
            return false;
        }
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setShopId(shop.getId());
        f.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(f);
        return true;
    }

    public boolean check(Long userId, Long shopId) {
        Long count = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getShopId, shopId));
        return count != null && count > 0;
    }

    /** 我的收藏：直接复用店铺卡片的字段口径，前端不用两套渲染。 */
    public List<Map<String, Object>> list(Long userId) {
        List<Favorite> favorites = favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getId));
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> shopIds = favorites.stream().map(Favorite::getShopId).toList();
        Map<Long, Shop> shops = new LinkedHashMap<>();
        for (Shop s : shopMapper.selectBatchIds(shopIds)) {
            if (s.getDeletedAt() == null) {
                shops.put(s.getId(), s);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Favorite f : favorites) {
            Shop s = shops.get(f.getShopId());
            if (s == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("logo", s.getLogo());
            m.put("categoryId", s.getCategoryId());
            m.put("campus", s.getCampus());
            m.put("bizStatus", s.getBizStatus());
            m.put("auditStatus", s.getAuditStatus());
            m.put("minAmount", s.getMinAmount());
            m.put("packFee", s.getPackFee());
            m.put("deliveryFee", s.getDeliveryFee());
            m.put("score", s.getScore());
            m.put("monthSales", s.getMonthSales());
            m.put("openTime", s.getOpenTime());
            m.put("favoritedAt", f.getCreatedAt());
            rows.add(m);
        }
        return rows;
    }

    public void remove(Long userId, Long shopId) {
        Favorite exists = favoriteMapper.selectOne(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getShopId, shopId));
        if (exists == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "还没有收藏这家店");
        }
        favoriteMapper.deleteById(exists.getId());
    }
}
