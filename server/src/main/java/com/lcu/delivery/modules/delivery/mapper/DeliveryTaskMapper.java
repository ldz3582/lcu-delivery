package com.lcu.delivery.modules.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.delivery.entity.DeliveryTask;
import org.apache.ibatis.annotations.Mapper;

/** 配送任务（表 delivery_task） */
@Mapper
public interface DeliveryTaskMapper extends BaseMapper<DeliveryTask> {
}
