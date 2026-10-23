package com.lcu.delivery.modules.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.delivery.entity.RiderLocation;
import org.apache.ibatis.annotations.Mapper;

/** 骑手轨迹（表 rider_location） */
@Mapper
public interface RiderLocationMapper extends BaseMapper<RiderLocation> {
}
