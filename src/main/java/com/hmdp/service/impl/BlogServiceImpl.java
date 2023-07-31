package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private BlogMapper blogMapper;

    public void queryBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        //2.查询blog有关的用户
        this.queryBlogUser(blog);
        //3.查询blog是否被点赞
        isLiked(blog);
        return Result.ok(blog);
    }

    private void isLiked(Blog blog) {
        //1.获取当前登录用户
        UserDTO user = UserHolder.getUser();
        //用户未登录访问首页的情况
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        //2.判断当前用户是否已经点过赞
        String key = "blog:like" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            //查询是否被点赞
            isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点过赞
        String key = "blog:like" + id.toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //没点过赞,数据库点赞数+1
            LambdaQueryWrapper<Blog> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Blog::getId, id);
            Blog blog = blogMapper.selectById(id);
            blog.setLiked(blog.getLiked() + 1);
            int count = blogMapper.updateById(blog);
            //存入redis的set集合，做重复判断
            if (count == 1) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //点过赞,数据库点赞数-1
            LambdaQueryWrapper<Blog> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Blog::getId, id);
            Blog blog = blogMapper.selectById(id);
            blog.setLiked(blog.getLiked() - 1);
            int count = blogMapper.updateById(blog);
            //删除redis set集合中的userId
            if (count == 1) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result getUserTop5(Long id) {
        String key = "blog:like" + id.toString();
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (set == null || set.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
