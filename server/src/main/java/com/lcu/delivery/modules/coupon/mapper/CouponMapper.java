package com.lcu.delivery.modules.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.coupon.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;

/** 优惠券模板（表 coupon） */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
}
