package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Object queryById(Long id) {
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopStr)) {
            //2.如果redis中存在, 直接返回
            Shop shopJson = JSONUtil.toBean(shopStr, Shop.class);
            return Result.ok(shopJson);
        }
        //3.如果redis中不存在, 则查询数据库
        Shop shop = getById(id);
        //4.如果数据库中不存在, 则返回错误
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //5.将查询到的店铺信息写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        //6.返回店铺信息
        return Result.ok(shop);
    }
}
