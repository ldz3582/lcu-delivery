package com.lcu.delivery.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcu.delivery.modules.admin.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/** 平台配置（表 sys_config） */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
