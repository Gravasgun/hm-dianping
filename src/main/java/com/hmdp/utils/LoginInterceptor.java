package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //从ThreadLocal中获取user，判断user是否为空
        if (UserHolder.getUser() == null) {
            //user为空，设置状态码，并拦截
            response.setStatus(401);
            return false;
        }
        //user不为空，放行
        return true;
    }
}
