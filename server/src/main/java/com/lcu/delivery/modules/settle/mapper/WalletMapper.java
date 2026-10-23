package com.lcu.delivery.modules.settle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.settle.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;

/** 钱包（表 wallet） */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {
}
