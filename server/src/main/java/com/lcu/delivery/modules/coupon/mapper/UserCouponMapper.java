package com.lcu.delivery.modules.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;

/** 用户券（表 user_coupon） */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}
