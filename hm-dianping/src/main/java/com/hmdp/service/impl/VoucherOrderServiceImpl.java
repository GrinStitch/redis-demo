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
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    //添加阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //添加线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //处理订单
                    proxy.handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.info("订单处理线程被中断", e);
                }
            }
        }
    }

    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        //基于Redisson的分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
        boolean isLocked = lock.tryLock();
        if (isLocked == false) {
            log.info("每位用户限购一单!");
            return;
        }
        try {
            Long voucherId = voucherOrder.getVoucherId();
            Long userId = voucherOrder.getUserId();
            //一人一单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                log.info("每位用户限购一单!");
                return;
            }
            //减库存(乐观锁)
            boolean flag = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!flag) {
                log.info("库存不足!");
                return;
            }
            save(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result seckillVoucher(Long voucherId) {
        //执行 Lua 脚本
        //获取用户 ID
        Long userId = UserHolder.getUser().getId();
        Long flag = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString()
        );
        int result = flag.intValue();
        //判断库存是否充足, 用户是否下单
        if (result != 0) {
            return Result.fail(flag == 1 ? "库存不足!" : "每位用户限购一单!");
        }
        //创建代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置用户ID
        voucherOrder.setUserId(userId);
        //设置订单ID
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //设置代金券ID
        voucherOrder.setVoucherId(voucherId);
        //把当前订单放到阻塞队列
        orderTasks.add(voucherOrder);
        //返回订单ID
        return Result.ok(orderId);
    }

/*    @Override
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
        Long userId = UserHolder.getUser().getId();

*//*        //悲观锁
        synchronized (userId.toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*//*

        //基于Radis的简易分布式锁
*//*        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean flag = lock.tryLock(1000L);
        if(flag == false){
            return Result.fail("每位用户限购一单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }*//*

        //基于Redisson的分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();
        if(flag == false){
            return Result.fail("每位用户限购一单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }*/

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        //3.5一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("每位用户限购一单!");
        }
        //4.减库存(乐观锁)
        boolean flag = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!flag) {
            return Result.fail("库存不足!");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1.设置用户ID
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
