package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeOutSec) {
        //获取线程标识
        String threadId = VALUE_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        //获取线程标识
        String threadId = VALUE_PREFIX + Thread.currentThread().getId();
        //获取redis中存的线程标识
        String cacheID = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(cacheID)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
