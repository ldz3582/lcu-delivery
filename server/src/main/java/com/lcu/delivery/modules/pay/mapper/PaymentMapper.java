package com.lcu.delivery.modules.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.pay.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/** 支付单（表 payment） */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
