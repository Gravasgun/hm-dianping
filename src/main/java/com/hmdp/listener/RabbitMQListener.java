package com.hmdp.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.RedisConstants;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQListener {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ShopMapper shopMapper;

    @RabbitListener(queues = "shop.queue")
    public void rebuildShopCache(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getId, id);
        Shop shop = shopMapper.selectOne(queryWrapper);
        redisTemplate.opsForValue().set(key, shop.toString());
    }
}
