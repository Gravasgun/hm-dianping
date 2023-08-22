package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.redisson.RedissonReadWriteLock;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        //工具类封装
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存穿透
        //Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        //1.根据shopId从redis查缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存在
        if (shopJson != null && !"".equals(shopJson)) {
            //3.缓存存在，不为空字符串，直接返回给前端
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //4.缓存存在，且为空字符串
        if (shopJson != null && "".equals(shopJson)) {
            return null;
        }
        //5.缓存不存在，尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean flag = tryLock(lockKey);
            //6.获取锁失败，递归，一直获取锁
            if (!flag) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //7.获取锁成功，查数据库
            LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Shop::getId, id);
            shop = shopMapper.selectOne(queryWrapper);
            //8.判断数据库中shop是否存在
            if (shop == null) {
                //9.数据库中shop不存在，将空字符串存入redis，解决缓存穿透问题
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //10.数据库中shop存在，先写入redis缓存，再返回给前端
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //11.释放锁
            unlock(lockKey);
        }
        return shop;
    }

    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithPassThrough(Long id) {
        //1.根据shopId从redis查缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否存在
        if (shopJson != null && !"".equals(shopJson)) {
            //3.缓存存在，不为空字符串，直接返回给前端
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //4.缓存存在，且为空字符串
        if (shopJson != null && "".equals(shopJson)) {
            return null;
        }
        //5.缓存不存在，查数据库
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getId, id);
        Shop shop = shopMapper.selectOne(queryWrapper);
        //6.判断数据库中shop是否存在
        if (shop == null) {
            //7.数据库中shop不存在，将空字符串存入redis，解决缓存穿透问题
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //8.数据库中shop存在，先写入redis缓存，再返回给前端
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    public Shop queryWithLogicalExpire(Long id) {
        //1.从redis中查询缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        //2.判断缓存是否命中，未命中，返回空
        if (shopJson == null) {
            return null;
        }
        //3.缓存命中，反序列化对象，取出对象中的过期时间和数据
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //4.判断缓存是否过期
        //5.缓存未过期，返回商铺数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //6.缓存过期，尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //7.判断是否获取锁
        if (flag) {
            //8.获取到了锁，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //9.重建缓存
                    this.saveDateToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //10.释放锁
                    unlock(lockKey);
                }
            });
        }
        //11.未获取到锁，返回过期数据
        return shop;
    }

    public void saveDateToRedis(Long id, Long expireSeconds) {
        //1.查数据库
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getId, id);
        Shop shop = shopMapper.selectOne(queryWrapper);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

//    /**
//     * 延时双删
//     * @param shop
//     * @return
//     */
//    @Override
//    @Transactional
//    public Result update(Shop shop) {
//        Long id = shop.getId();
//        if (id == null) {
//            return Result.fail("店铺id不能为空！");
//        }
//        //1.删除缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        stringRedisTemplate.delete(key);
//        //2.操作数据库
//        shopMapper.updateById(shop);
//        //3.删除缓存
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        stringRedisTemplate.delete(key);
//        return Result.ok();
//    }

    /**
     * 分布式锁
     *
     * @param shop
     * @return
     */
//    @Override
//    @Transactional
//    public Result update(Shop shop) {
//        Long id = shop.getId();
//        if (id == null) {
//            return Result.fail("店铺id不能为空！");
//        }
//        //1.获取写锁
//        RLock writeLock = redissonClient.getReadWriteLock("readWriteLock").writeLock();
//        //2.加写锁
//        writeLock.lock();
//        //3.删除缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        stringRedisTemplate.delete(key);
//        //4.操作数据库
//        shopMapper.updateById(shop);
//        writeLock.unlock();
//        return Result.ok();
//    }

    /**
     * mq异步更新缓存
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        //1.删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        //2.操作数据库
        shopMapper.updateById(shop);
        //3.通知mq异步更新缓存
        String queueName = "shop.queue";
        rabbitTemplate.convertAndSend(queueName,id);
        return Result.ok();
    }
}