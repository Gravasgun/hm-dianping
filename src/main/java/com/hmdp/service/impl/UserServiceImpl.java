package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式不正确！");
        }
        //3.如果符合，生成验证码
        //randomUtil是hutool依赖中提供的工具类
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到redis中，并设置code的有效期，避免时间过长占用内存
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        //6.返回成功结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        //校验失败，返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误！");
        }

        //2.从redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //验证码为空的情况
        if (code == null) {
            return Result.fail("验证码为空！");
        }
        //验证码错误的情况
        if (!code.equals(cacheCode)) {
            return Result.fail("验证码错误！");
        }

        //3.验证码正确，根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = userMapper.selectOne(queryWrapper);

        //4.判断用户是否存在
        //用户不存在,创建一个新用户对象，并将用户信息保存到数据库中
        if (user == null) {
            user = createUser(phone);
        }

        //5.用户存在,将用户保存到redis中
        //生成随机的token，作为redis的key
        String token = UUID.randomUUID(true).toString();
        //复制user对象(解决浏览器中信息泄露的问题)
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将复制的对象转换成一个map作为redis的value
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置token的有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //6.返回token给前端
        return Result.ok(token);
    }

    @Override
    public Result logout() {
        return null;
    }

    public User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(4));
        //将用户信息保存到数据库中
        userMapper.insert(user);
        return user;
    }
}
