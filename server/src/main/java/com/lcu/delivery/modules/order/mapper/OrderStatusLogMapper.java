package com.lcu.delivery.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;

/** 订单状态流水（表 order_status_log） */
@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {
}
