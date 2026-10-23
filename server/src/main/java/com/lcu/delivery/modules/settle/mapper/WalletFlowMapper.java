package com.lcu.delivery.modules.settle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.settle.entity.WalletFlow;
import org.apache.ibatis.annotations.Mapper;

/** 资金流水（表 wallet_flow） */
@Mapper
public interface WalletFlowMapper extends BaseMapper<WalletFlow> {
}
