package com.lcu.delivery.modules.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.shop.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/** 店铺（表 shop） */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
