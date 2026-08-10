package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopTypeMapper shopTypeMapper;

    @Override
    public List<ShopType> queryList() {
        //1.判断redis缓存中是否已有店铺类型列表
        String shopTypeListStr = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_CACHE_KEY);
        if (StrUtil.isNotBlank(shopTypeListStr)) {
            //2.返回缓存中的数据
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListStr, ShopType.class);
            return shopTypeList;
        }
        //3.如果redis中不存在, 则查询数据库
        List<ShopType> shopTypeList = shopTypeMapper.list();
        //4.将数据库查询结果写入redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_CACHE_KEY, JSONUtil.toJsonStr(shopTypeList));
        return shopTypeList;
    }
}
