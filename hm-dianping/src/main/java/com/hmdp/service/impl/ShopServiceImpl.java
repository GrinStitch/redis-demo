package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
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
import java.util.concurrent.TimeUnit;

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
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //利用互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //缓存穿透
    public Shop querywithPassThrough(Long id){
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopStr)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(shopStr, Shop.class);
        }
        //2.5如果redis中存在空值, 直接返回
        if (shopStr != null){
            return null;
        }
        //3.如果redis中不存在, 则查询数据库
        Shop shop = getById(id);
        //4.如果数据库中不存在, 则返回错误
        if (shop == null) {
            //4.5将空值写入redis(简单地处理缓存穿透问题)
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.将查询到的店铺信息写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回店铺信息
        return shop;
    }

    //缓存击穿
    public Shop queryWithMutex(Long id){
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopStr)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(shopStr, Shop.class);
        }
        //2.5如果redis中存在空值, 直接返回
        if (shopStr != null){
            return null;
        }
        try {
            //获取互斥锁
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if(lock == false){
                //获取锁失败
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //3.如果redis中不存在, 则查询数据库
            Shop shop = getById(id);
            //4.如果数据库中不存在, 则返回错误
            if (shop == null) {
                //4.5将空值写入redis(简单地处理缓存穿透问题)
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                        RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5.将查询到的店铺信息写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //6.返回店铺信息
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(RedisConstants.LOCK_SHOP_KEY + id);
        }
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
