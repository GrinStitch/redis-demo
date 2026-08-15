package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
/*        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id,
                Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);*/

        //利用互斥锁解决缓存击穿
/*        cacheClient.queryWithMutex(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id,
                Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);*/

        //利用逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id,
                Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //if(x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        //}
        /*//计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        //查询redis
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok();
        }
        //解析数据
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = results.getContent();
        if(contents.size() <= from) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(contents.size());
        Map<Long, Distance> map = new HashMap<>(contents.size());
        //截取数据
        contents.stream().skip(from).forEach(content -> {
            //获取店铺id
            String shopId = content.getContent().getName();
            //获取距离
            Distance distance = content.getDistance();
            ids.add(Long.valueOf(shopId));
            map.put(Long.valueOf(shopId), distance);
        });
        //根据id查询店铺
        List<Shop> shops = query().in("id", ids)
                .last("ORDER BY FIELD(id," + StrUtil.join(",", ids) + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(map.get(shop.getId()).getValue());
        }
        return Result.ok(shops);*/
    }

    /*
    //缓存穿透
    public Shop queryWithPassThrough(Long id) {
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopStr)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(shopStr, Shop.class);
        }
        //2.5如果redis中存在空值, 直接返回
        if (shopStr != null) {
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
    public Shop queryWithMutex(Long id) {
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopStr)) {
            //2.如果redis中存在, 直接返回
            return JSONUtil.toBean(shopStr, Shop.class);
        }
        //2.5如果redis中存在空值, 直接返回
        if (shopStr != null) {
            return null;
        }
        try {
            //获取互斥锁
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (lock == false) {
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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期
    public Shop queryWithLogicalExpire(Long id) {
        //1.先在redis中查询是否有该店铺的缓存, 如果有, 直接返回
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //按照缓存是否命中
        if (StrUtil.isBlank(shopStr)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = BeanUtil.toBean(redisData.getData(), Shop.class);
        //判断缓存是否过期,如果没有过期就直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //缓存过期,获取互斥锁
        boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
        //如果成功获取锁，那就新开一个线程来把数据库的结果放到缓存中
        if (lock == true) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id, RedisConstants.CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(RedisConstants.LOCK_SHOP_KEY + id);
                }

            });
        }
        //返回过期的店铺信息
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
*/

    public void saveShop2Redis(long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        //stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
