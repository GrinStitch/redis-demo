package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit timeUtil) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUtil);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUtil) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUtil.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> function, Long time, TimeUnit timeUtil) {
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(json, type);
        }
        //2.5如果redis中存在空值, 直接返回
        if (json != null) {
            return null;
        }
        //3.如果redis中不存在, 则查询数据库
        R r = function.apply(id);
        //4.如果数据库中不存在, 则返回错误
        if (r == null) {
            //4.5将空值写入redis(简单地处理缓存穿透问题)
            stringRedisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
            return null;
        }
        //5.将查询到的店铺信息写入redis
        this.set(key, r, time, timeUtil);
        //6.返回店铺信息
        return r;
    }

    //缓存击穿(逻辑处理)
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                            Function<ID, R> function, Long time, TimeUnit timeUtil) {
        String key = keyPrefix + id;
        String locKey = lockPrefix + id;
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String json = stringRedisTemplate.opsForValue().get(key);
        //按照缓存是否命中
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        //判断缓存是否过期,如果没有过期就直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //缓存过期,获取互斥锁
        boolean lock = tryLock(locKey);
        //如果成功获取锁，那就新开一个线程来把数据库的结果放到缓存中
        if (lock == true) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R r1 = function.apply(id);
                    this.setWithLogicalExpire(key, r1, time, timeUtil);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(locKey);
                }

            });
        }
        //返回过期信息
        return r;
    }

    //缓存击穿(互斥锁)
    public <R, ID> R queryWithMutex(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                    Function<ID, R> function, Long time, TimeUnit timeUtil) throws InterruptedException {
        String key = keyPrefix + id;
        String locKey = lockPrefix + id;
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(json, type);
        }
        //2.5如果redis中存在空值, 直接返回
        if (json != null) {
            return null;
        }

        //获取互斥锁
        boolean lock = tryLock(locKey);
        if (lock == false) {
            //获取锁失败
            Thread.sleep(50);
            return queryWithMutex(keyPrefix, lockPrefix, id, type, function, time, timeUtil);
        }
        try {
            //3.如果redis中不存在, 则查询数据库
            R r = function.apply(id);
            //4.如果数据库中不存在, 则返回错误
            if (r == null) {
                //4.5将空值写入redis(简单地处理缓存穿透问题)
                stringRedisTemplate.opsForValue().set(key, "", time, timeUtil);
                return null;
            }
            //5.将查询到的店铺信息写入redis
            this.set(key, r, time, timeUtil);
            //6.返回店铺信息
            return r;
        } finally {
            //释放锁
            unlock(locKey);
        }
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
