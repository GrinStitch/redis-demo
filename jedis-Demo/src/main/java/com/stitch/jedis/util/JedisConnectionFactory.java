package com.stitch.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {

    public static final JedisPool jedisPoll;

    static {
        //配置连接池
        JedisPoolConfig pollConfig = new JedisPoolConfig();
        //最大连接数
        pollConfig.setMaxTotal(10);
        //最大空闲连接数
        pollConfig.setMaxIdle(10);
        //最小空闲连接数
        pollConfig.setMinIdle(0);
        //获取连接时的最大等待毫秒数
        pollConfig.setMaxWaitMillis(1000);
        //创建连接池对象
        jedisPoll = new JedisPool(pollConfig,
                "localhost", 6379, 1000, "123456");
    }

    public static Jedis getJedis() {
        return jedisPoll.getResource();
    }
}
