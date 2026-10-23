package com.lcu.delivery.modules.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.delivery.entity.Rider;
import org.apache.ibatis.annotations.Mapper;

/** 骑手（表 rider） */
@Mapper
public interface RiderMapper extends BaseMapper<Rider> {
}
