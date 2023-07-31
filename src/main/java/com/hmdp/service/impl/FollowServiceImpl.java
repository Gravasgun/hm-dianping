package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;
    @Override
    public Result tryFollowUser(Long userFollowedId, Boolean isFollowed) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follow:userId:" + userId;
        // 关注用户 新增数据
        if (isFollowed) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(userFollowedId);
            boolean success = save(follow);
            if (success) {
                stringRedisTemplate.opsForSet().add(key, userFollowedId.toString());
            }
        } else {
            // 取关用户 删除数据
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId);
            queryWrapper.eq(Follow::getFollowUserId, userFollowedId);
            int num = followMapper.delete(queryWrapper);
            if (num == 1) {
                stringRedisTemplate.opsForSet().remove(key, userFollowedId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result getIsFollowed(Long id) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 查询
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId, userId);
        queryWrapper.eq(Follow::getFollowUserId, id);
        Follow follow = followMapper.selectOne(queryWrapper);
        if (follow == null) {
            return Result.ok(false);
        } else {
            return Result.ok(true);
        }
    }

    @Override
    public Result getCommonUser(Long id) {
        Long userId = UserHolder.getUser().getId();
        //当前登录用户的关注列表集合
        String key1 = "follow:userId:" + userId;
        //点击查看的用户的关注列表集合
        String key2 = "follow:userId:" + id;
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //批量查询用户并转换为userDTO对象
        List<UserDTO> userDTOList = userService.listByIds(ids).stream().map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
