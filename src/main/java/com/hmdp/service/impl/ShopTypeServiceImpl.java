package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private ShopTypeMapper mapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result quertTypeListCache() {
        //1.从redis中查缓存
        String key = RedisConstants.CACHE_SHOP_KEY + "list";
        String value = stringRedisTemplate.opsForValue().get(key);
        //2.查到了缓存，直接返回给前端(value是一个json数组，需要使用工具类将json数组转换为java中的集合后再返回给前端)
        if (value != null) {
            return Result.ok(JSONUtil.toList(value, ShopType.class));
        }
        //3.没查到缓存，从数据库中查询list
        LambdaQueryWrapper<ShopType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(ShopType::getSort);
        List<ShopType> shopTypes = mapper.selectList(queryWrapper);
        //4.将查询的list放入redis缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));
        //5.返回list给前端
        return Result.ok(shopTypes);
    }
}
