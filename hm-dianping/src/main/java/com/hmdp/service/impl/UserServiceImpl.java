package com.hmdp.service.impl;

import cn.hutool.core.util.CreditCodeUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式有误!");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存到session中
        session.setAttribute("code", code);
        session.setAttribute("phone", phone);
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
        //2.5检验两次输入的手机号是否一致
        Object phoneInSession = session.getAttribute("phone");
        if(phoneInSession == null || !phoneInSession.equals(loginForm.getPhone())){
            return Result.fail("手机号与验证码不匹配!");
        }
        //3.判断验证码是否正确
        Object code = session.getAttribute("code");
        if(code == null || !code.toString().equals(loginForm.getCode())){
            return Result.fail("验证码错误!");
        }
        //4.判断当前用户是否已经存在
        User user = query().eq("phone", loginForm.getPhone()).one();
        if(user == null){
            user = createUserWithPhone(loginForm.getPhone());
        }
        //5.把用户信息保存到session中
        session.setAttribute("user", user);
        session.removeAttribute("code");
        session.removeAttribute("phone");
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User newUser  = new User();
        newUser.setPhone(phone);
        newUser.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(newUser);
        return newUser;
    }
}
