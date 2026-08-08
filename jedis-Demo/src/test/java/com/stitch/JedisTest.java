package com.stitch;

import com.stitch.jedis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisTest {

    private Jedis jedis;

    @BeforeEach
    public void setUp() {
        jedis = JedisConnectionFactory.getJedis();
        jedis.select(2);
    }

    @Test
    public void test() {
        String result = jedis.set("name", "stitch");
        System.out.println("result = " + result);
        String name = jedis.get("name");
        System.out.println(name);
    }

    @Test
    public void test2() {
        jedis.hset("user:4", "name", "zhangsan");
        jedis.hset("user:4", "age", "19");
        Map<String, String> all = jedis.hgetAll("user:4");
        System.out.println(all);
    }

    @AfterEach
    public void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
