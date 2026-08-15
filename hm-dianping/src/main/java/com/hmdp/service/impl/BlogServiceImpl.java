package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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
    @Resource
    private IFollowService followService;

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
    public Result saveBlog(Blog blog) {
        if(blog.getTitle() == null){
            return Result.fail("标题不能为空!");
        }else if(blog.getContent() == null){
            return Result.fail("内容不能为空!");
        }else if(blog.getShopId() == null){
            return Result.fail("店铺不能为空!");
        }
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("保存失败!");
        }
        //获取该用户的粉丝
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : followUserId) {
            //获取粉丝Id
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
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


    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户Id
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key = FEED_KEY + userId;
        //查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据: blogId, minTime(时间戳), offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        Long minTime = 0L;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取blogId
            String value = typedTuple.getValue();
            Long blogId = Long.valueOf(value);
            ids.add(blogId);
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }{
                //获取minTime
                minTime = typedTuple.getScore().longValue();
                os = 1;
            }
        }
        //根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        //判断blog是否被点赞
        for (Blog blog : blogs) {
            //查询博客用户
            queryBlogUser(blog);
            //判断当前用户是否已点赞
            isBlogLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
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
}
