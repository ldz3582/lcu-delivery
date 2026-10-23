package com.lcu.delivery.modules.settle;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.settle.entity.Settlement;
import com.lcu.delivery.modules.settle.entity.Wallet;
import com.lcu.delivery.modules.settle.entity.WalletFlow;
import com.lcu.delivery.modules.settle.mapper.SettlementMapper;
import com.lcu.delivery.modules.settle.mapper.WalletFlowMapper;
import com.lcu.delivery.modules.settle.mapper.WalletMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算：订单完成即生成待结算单；冻结期满后入钱包并记流水（幂等键：settlement.orderId）。
 */
@Service
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final WalletMapper walletMapper;
    private final WalletFlowMapper flowMapper;

    public SettlementService(SettlementMapper settlementMapper, WalletMapper walletMapper,
                             WalletFlowMapper flowMapper) {
        this.settlementMapper = settlementMapper;
        this.walletMapper = walletMapper;
        this.flowMapper = flowMapper;
    }

    @Transactional
    public void createForOrder(Order order) {
        Long exists = settlementMapper.selectCount(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        Settlement s = new Settlement();
        s.setOrderId(order.getId());
        s.setShopId(order.getShopId());
        s.setRiderId(order.getRiderId());
        s.setGoodsAmount(order.getGoodsAmount());
        s.setCommission(order.getCommission());
        s.setShopIncome(order.getShopIncome());
        s.setRiderIncome(order.getRiderIncome());
        s.setStatus(0);
        s.setCreatedAt(LocalDateTime.now());
        settlementMapper.insert(s);
    }

    public List<Settlement> dueList(LocalDateTime before) {
        return settlementMapper.selectList(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getStatus, 0)
                .le(Settlement::getCreatedAt, before));
    }

    @Transactional
    public void settle(Settlement s) {
        s.setStatus(1);
        s.setSettleAt(LocalDateTime.now());
        settlementMapper.updateById(s);
        credit(2, s.getShopId(), s.getShopIncome(), "SHOP_SETTLE", String.valueOf(s.getId()), "商家货款结算");
        if (s.getRiderId() != null && s.getRiderIncome() != null && s.getRiderIncome() > 0) {
            credit(3, s.getRiderId(), s.getRiderIncome(), "RIDER_SETTLE", String.valueOf(s.getId()), "骑手配送收入");
        }
    }

    private void credit(int ownerType, Long ownerId, Integer amount, String bizType, String bizNo, String remark) {
        if (amount == null || amount <= 0) {
            return;
        }
        Wallet wallet = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, ownerType).eq(Wallet::getOwnerId, ownerId));
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setOwnerType(ownerType);
            wallet.setOwnerId(ownerId);
            wallet.setBalance(0);
            wallet.setFrozen(0);
            wallet.setVersion(0);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletMapper.insert(wallet);
        }
        Long dup = flowMapper.selectCount(Wrappers.<WalletFlow>lambdaQuery()
                .eq(WalletFlow::getBizType, bizType).eq(WalletFlow::getBizNo, bizNo)
                .eq(WalletFlow::getWalletId, wallet.getId()));
        if (dup != null && dup > 0) {
            return;
        }
        int after = wallet.getBalance() + amount;
        wallet.setBalance(after);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletMapper.updateById(wallet);

        WalletFlow flow = new WalletFlow();
        flow.setWalletId(wallet.getId());
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setDirection(1);
        flow.setAmount(amount);
        flow.setBalanceAfter(after);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(flow);
    }
}
