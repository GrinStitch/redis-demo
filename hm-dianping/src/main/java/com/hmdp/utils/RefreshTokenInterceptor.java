package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/*        //1.获取session
        HttpSession session = request.getSession();
        //2.判断session中是否包含用户
        Object user = session.getAttribute("user");
        if(user == null){
            response.setStatus(401);
            return false;
        }*/

        //1.从请求头中获取token
        String token = request.getHeader("authorization");
        //1.5判断token是否为空
        if (StrUtil.isBlank(token)) {
            //token为空, 直接放行
            return true;
        }
        //2.从redis中获取用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            //用户不存在, 直接放行
            return true;
        }
        UserDTO userDTO = BeanUtil.toBean(userMap, UserDTO.class);
        //3.如果包含用户, 加入到线程中
        UserHolder.saveUser(userDTO);
        //4.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 1000000000000000000L, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
