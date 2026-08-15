package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    private static final String FOLLOW_KEY = "follow:";

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //判断是关注还是取关
        String key = FOLLOW_KEY + userId;
        if(isFollow == false){
            //取关
            boolean res = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (res) {
                //将取关用户移除redis
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }else{
            //关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean res = save(follow);
            if (res) {
                //将关注用户放到redis当中
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 判断是否关注
        Long userId = UserHolder.getUser().getId();
        Follow follow = getOne(new QueryWrapper<Follow>()
                .eq("user_id", userId).eq("follow_user_id", followUserId));
        if(follow == null){
            // 没有关注
            return Result.ok(false);
        }
        // 关注了
        return Result.ok(true);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户的Id
        Long userId = UserHolder.getUser().getId();
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOW_KEY + userId, FOLLOW_KEY + id);
        if (intersect == null || intersect.isEmpty()) {
            //空集合
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //查询数据库
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(item -> BeanUtil.copyProperties(item, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
