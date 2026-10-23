package com.lcu.delivery.modules.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.shop.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/** 商品（表 product） */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
