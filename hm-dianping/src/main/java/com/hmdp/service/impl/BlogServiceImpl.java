package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //根据 ID 查询博客
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("博客不存在!");
        }
        //查询博客用户
        queryBlogUser(blog);
        //判断当前用户是否已点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            //用户未登录;
            return;
        }
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double result = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(result == null){
            //如果当前用户未点赞，可以点赞
            boolean flag = update().setSql("liked = liked + 1").eq("id", id).update();
            if(flag){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else{
            //如果当前用户已点赞，取消点赞
            boolean flag = update().setSql("liked = liked - 1").eq("id", id).update();
            if(flag){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询Top5点赞用户
        List<Long> userId = stringRedisTemplate.opsForZSet().range(key, 0, 4).stream().map(Id -> {
            return Long.valueOf(Id);
        }).collect(Collectors.toList());
        //判断用户集合是否为空
        if (userId == null || userId.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //查询数据库
        String idStr = StrUtil.join(",", userId);
        List<UserDTO> userDTO = userService.query()
                .in("id", userId)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream().map(user -> {return BeanUtil.copyProperties(user, UserDTO.class);
        }).collect(Collectors.toList());
        return Result.ok(userDTO);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
