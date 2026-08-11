package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long time) {
        //获取当前线程Id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, time, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock() {
        //执行lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT, Collections.singletonList(LOCK_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

/*    @Override
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + name);
        //判断线程Id是否一致
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(LOCK_PREFIX + name);
        }
    }*/
}
