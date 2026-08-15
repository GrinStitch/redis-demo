package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);


    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    Result updateShop(Shop shop);

    /**
     * 根据类型查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @param x 经度
     * @param y 纬度
     * @return 商铺列表
     * @param current
     * @param x
     * @param y
     * @return
     * @param typeId
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
