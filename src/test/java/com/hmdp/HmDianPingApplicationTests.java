package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testShopRedis() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 10; i++) {
                System.out.println("id=" + redisIdWorker.nextId("order"));
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            executorService.submit(runnable);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - begin));
    }

    @Test
    void testRedis(){
        redisTemplate.opsForValue().set("username","lisi");
    }
}
