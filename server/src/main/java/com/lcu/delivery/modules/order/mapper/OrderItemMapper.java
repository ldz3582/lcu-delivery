package com.lcu.delivery.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/** 订单明细（表 order_item） */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
