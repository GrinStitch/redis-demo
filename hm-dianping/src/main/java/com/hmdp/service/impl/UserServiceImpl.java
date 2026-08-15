package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.CreditCodeUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式有误!");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

/*        //4.保存到session中
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);*/

        //4.将验证码保存到Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY  +
                phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.info("发送给{}的验证码: {}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.先校验手机号格式
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式有误!");
        }

/*        //2.5检验两次输入的手机号是否一致
        Object phoneInSession = session.getAttribute("phone");
        if(phoneInSession == null || !phoneInSession.equals(loginForm.getPhone())){
            return Result.fail("手机号与验证码不匹配!");
        }*/

        //2.5从Redis里面获取code
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        //3.判断验证码是否正确
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail("验证码错误!");
        }
        //4.判断当前用户是否已经注册
        User user = query().eq("phone", loginForm.getPhone()).one();
        if(user == null){
            user = createUserWithPhone(loginForm.getPhone());
        }

/*        //5.把用户信息保存到session中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);
        session.removeAttribute("code");
        session.removeAttribute("phone");*/

        //6.给每一位用户设置一个token, 作为令牌
        String token = UUID.randomUUID().toString(true);
        //7.1将User转化为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap  = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //7.2将user存入Redis
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.3设置token的过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        stringRedisTemplate.opsForHash().delete(RedisConstants.LOGIN_USER_KEY, request.getHeader("authorization"));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User newUser  = new User();
        newUser.setPhone(phone);
        newUser.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        newUser.setCreateTime(LocalDateTime.now());
        newUser.setUpdateTime(LocalDateTime.now());
        save(newUser);
        return newUser;
    }
}
