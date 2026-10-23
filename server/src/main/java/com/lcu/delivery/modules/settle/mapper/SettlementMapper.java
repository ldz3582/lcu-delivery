package com.lcu.delivery.modules.settle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.settle.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;

/** 结算单（表 settlement） */
@Mapper
public interface SettlementMapper extends BaseMapper<Settlement> {
}
