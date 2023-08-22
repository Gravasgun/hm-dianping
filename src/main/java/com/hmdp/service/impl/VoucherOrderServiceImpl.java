package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private SeckillVoucherMapper voucherMapper;
    @Autowired
    private VoucherOrderMapper orderMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Integer stock, SeckillVoucher voucher) {
        //5.一人一单功能
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //查询订单表
        LambdaQueryWrapper<VoucherOrder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId);
        Integer num = orderMapper.selectCount(lambdaQueryWrapper);
        if (num > 0) {
            return Result.fail("该用户已经下过一单，不允许重复下单");
        }
        //6.扣减库存
        stock--;
        //7.SeckillVoucher表中更新库存
        voucher.setStock(stock);
        LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId).gt(SeckillVoucher::getStock, 0);
        int count = voucherMapper.update(voucher, queryWrapper);
        if (count != 1) {
            return Result.fail("秒杀券库存不足！");
        }
        //8.创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //往order表中插入数据
        orderMapper.insert(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);
    }
}
