package com.lcu.delivery.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/** 收货地址（表 user_address） */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
