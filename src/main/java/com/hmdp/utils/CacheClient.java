package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.缓存存在，且不为空字符串，反序列化对象
        if (json != null && !json.equals("")) {
            return JSONUtil.toBean(json, type);
        }
        //3.缓存存在，且为空字符串
        if (json != null && json.equals("")) {
            return null;
        }
        //4.缓存不存在，从数据库中查询
        R r = dbFallback.apply(id);
        //5.判断数据库中是否能查询到信息
        if (r == null) {
            //6.查不到数据，将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //7.查到了数据，写入redis
        this.set(key, r, time, unit);
        return r;
    }

    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1.从redis中查询缓存
        String cacheKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        //2.判断缓存是否命中，未命中，返回空
        if (json == null) {
            return null;
        }
        //3.缓存命中，反序列化对象，取出对象中的过期时间和数据
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        //4.判断缓存是否过期
        //5.缓存未过期，返回商铺数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //6.缓存过期，尝试获取锁
        String lockKey = lockKeyPrefix + id;
        boolean flag = tryLock(lockKey);
        //7.判断是否获取锁
        if (flag) {
            //8.获取到了锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //9.重建缓存
                    R r1 = dbFallback.apply(id);
                    //10.写入redis
                    setWithLogicalExpire(cacheKey, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //11.释放锁
                    unlock(lockKey);
                }
            });
        }
        //12.未获取到锁，返回过期数据
        return r;
    }
}