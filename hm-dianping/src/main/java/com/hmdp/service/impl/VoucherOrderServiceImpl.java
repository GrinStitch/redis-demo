package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断当前时间是否在秒杀范围内
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始!");
        }
        if(endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束!");
        }
        //3.判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("库存不足!");
        }
        //4.减库存
        seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        boolean flag = seckillVoucherService.updateById(seckillVoucher);
        if(!flag){
            return Result.fail("库存不足!");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1.获取用户ID
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //5.2.设置订单ID
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.3.设置代金券ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
