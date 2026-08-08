package com.stitch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class SpringDataRedisDemoApplicationTests {

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	@Test
	void RedisTest() {
		ValueOperations ops = redisTemplate.opsForValue();
		ops.set("name", "stitch2");
		System.out.println(ops.get("name"));
	}

}
