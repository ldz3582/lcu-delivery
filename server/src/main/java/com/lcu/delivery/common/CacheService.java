package com.lcu.delivery.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 轻量 Redis 缓存（需求 5.2「Redis 缓存首页商家列表、商品信息、订单状态」）。
 * 设计原则：**缓存永远不能让主流程失败**——Redis 不可用/序列化异常一律回退直查数据库。
 */
@Component
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public CacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 读缓存，未命中则执行 loader 并回填。任何异常都回退到 loader。 */
    public <T> T get(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return json.readValue(cached, type);
            }
        } catch (Exception e) {
            log.debug("缓存读取跳过 {}：{}", key, e.getMessage());
        }
        T value = loader.get();
        try {
            if (value != null) {
                redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
            }
        } catch (Exception e) {
            log.debug("缓存写入跳过 {}：{}", key, e.getMessage());
        }
        return value;
    }

    /** 主动失效（商品/店铺变更时调用）。 */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.debug("缓存失效跳过 {}：{}", key, e.getMessage());
        }
    }

    /** 按前缀批量失效（演示用，生产建议 SCAN）。 */
    public void evictPrefix(String prefix) {
        try {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.debug("缓存前缀失效跳过 {}：{}", prefix, e.getMessage());
        }
    }
}
