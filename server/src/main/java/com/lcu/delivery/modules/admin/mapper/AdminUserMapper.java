package com.lcu.delivery.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

/** 后台账号（表 admin_user） */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
