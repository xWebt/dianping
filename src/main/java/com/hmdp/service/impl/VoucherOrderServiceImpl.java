package com.hmdp.service.impl;

import cn.hutool.db.sql.Order;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private VoucherOrderServiceImpl selfProxy;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime() .isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if(voucher.getBeginTime() .isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经开始");
        }

        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        boolean b = simpleRedisLock.tryLock(1200L);
        if(!b){
             return Result.fail("不允许重复下单");
        }
        try {
            synchronized (userId.toString().intern()) {
                return selfProxy.creatVoucherOrder(voucherId);
            }
        }finally {
            simpleRedisLock.unLock();
        }

    }

    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();


        Integer count = query().eq("voucher_id", voucherId).eq("userId", userId).count();

        if (count > 0) {
            return Result.fail("仅能购买一单");
        }


        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();

        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        long userid = UserHolder.getUser().getId();
        voucherOrder.setUserId(userid);

        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);


        return Result.ok(orderId);

    }
}
