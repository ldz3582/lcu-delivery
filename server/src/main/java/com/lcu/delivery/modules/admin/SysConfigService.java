package com.lcu.delivery.modules.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.admin.entity.SysConfig;
import com.lcu.delivery.modules.admin.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台开关统一走这里：数据库中改，内存缓存 30 秒内生效，不重启不发版。
 * （规模化后可换成 Redis 缓存，见手册 4.9。）
 */
@Service
public class SysConfigService {

    private final SysConfigMapper mapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SysConfigService(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    @Scheduled(fixedDelay = 30_000)
    public void reload() {
        try {
            List<SysConfig> list = mapper.selectList(null);
            for (SysConfig c : list) {
                cache.put(c.getConfigKey(), c.getConfigValue());
            }
        } catch (Exception ignored) {
            // 库还没初始化时不影响启动
        }
    }

    public String get(String key, String def) {
        return cache.getOrDefault(key, def);
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public List<SysConfig> listAll() {
        return mapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getGroupKey));
    }

    public void update(String key, String value, Long operatorId) {
        SysConfig config = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "配置项不存在：" + key);
        }
        config.setConfigValue(value);
        config.setUpdatedBy(operatorId);
        config.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(config);
        cache.put(key, value);
    }
}
