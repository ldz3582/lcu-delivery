package com.lcu.delivery.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 用户（表 user） */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
