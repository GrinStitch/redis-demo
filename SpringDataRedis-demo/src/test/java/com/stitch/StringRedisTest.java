package com.stitch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stitch.bean.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
class StringRedisTest {

	@Autowired
	private StringRedisTemplate redisTemplate;

	private static final ObjectMapper mapper = new ObjectMapper();
	@Test
	void RedisTest() throws JsonProcessingException {
		//创建对象
		User user = new User("zhangsan", 18);
		//手动序列化
		String json = mapper.writeValueAsString(user);
		//写入数据
		redisTemplate.opsForValue().set("User:100", json);
		//获取数据
		String jsonUser = redisTemplate.opsForValue().get("User:100");
		//手动反序列化
		User value = mapper.readValue(jsonUser, User.class);
		System.out.println(value);
	}

	@Test
	void hashRedis(){
		HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
		ops.put("User:200", "name", "胡歌");
		ops.put("User:200", "age", "33");
		Map<Object, Object> map = ops.entries("User:200");
		System.out.println(map);
	}
}
