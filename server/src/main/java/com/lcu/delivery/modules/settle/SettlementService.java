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

    /** 钱包入账（外部模块用，如用户取消违约金补偿给商家）。 */
    @Transactional
    public void creditShop(Long shopId, int amount, String bizType, String bizNo, String remark) {
        credit(2, shopId, amount, bizType, bizNo, remark);
    }

    public Settlement findByOrder(Long orderId) {
        return settlementMapper.selectOne(Wrappers.<Settlement>lambdaQuery()
                .eq(Settlement::getOrderId, orderId).last("limit 1"));
    }

    /**
     * 全额退款冲正：结算单直接作废；若已打款，则从商家/骑手钱包扣回（余额不足则扣到 0 并记欠款）。
     * 幂等：status=2 直接返回。
     */
    @Transactional
    public void reverseFull(Long orderId, String reason) {
        Settlement s = findByOrder(orderId);
        if (s == null || (s.getStatus() != null && s.getStatus() == 2)) {
            return;
        }
        if (s.getStatus() != null && s.getStatus() == 1) {
            debit(2, s.getShopId(), s.getShopIncome(), "SETTLE_REVERSE", String.valueOf(s.getId()) + "-S",
                    "订单退款冲正商家结算");
            debit(3, s.getRiderId(), s.getRiderIncome(), "SETTLE_REVERSE", String.valueOf(s.getId()) + "-R",
                    "订单退款冲正骑手收入");
            s.setShopIncome(0);
            s.setRiderIncome(0);
        }
        s.setStatus(2);
        s.setReverseReason(reason);
        s.setReversedAt(LocalDateTime.now());
        settlementMapper.updateById(s);
    }

    /**
     * 部分退款冲减：结算单金额相应减少（未结算的直接改金额，已结算的从钱包扣回）。
     * bizNo 里带本次退款金额，保证多次部分退款各自幂等。
     */
    @Transactional
    public void reversePartial(Long orderId, int amount, String reason) {
        if (amount <= 0) {
            return;
        }
        Settlement s = findByOrder(orderId);
        if (s == null || (s.getStatus() != null && s.getStatus() == 2)) {
            return;
        }
        int shopIncome = s.getShopIncome() == null ? 0 : s.getShopIncome();
        int deduct = Math.min(amount, shopIncome);
        if (deduct <= 0) {
            return;
        }
        if (s.getStatus() != null && s.getStatus() == 1) {
            debit(2, s.getShopId(), deduct, "SETTLE_REVERSE", String.valueOf(s.getId()) + "-P" + amount,
                    "售后部分退款冲减商家结算");
        }
        s.setShopIncome(shopIncome - deduct);
        s.setReverseReason(reason);
        s.setReversedAt(LocalDateTime.now());
        settlementMapper.updateById(s);
    }

    /** 钱包扣款（可扣到 0，不足部分写在流水备注里，便于人工追缴）。 */
    private void debit(int ownerType, Long ownerId, Integer amount, String bizType, String bizNo, String remark) {
        if (ownerId == null || amount == null || amount <= 0) {
            return;
        }
        Wallet wallet = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getOwnerType, ownerType).eq(Wallet::getOwnerId, ownerId));
        if (wallet == null) {
            return;
        }
        Long dup = flowMapper.selectCount(Wrappers.<WalletFlow>lambdaQuery()
                .eq(WalletFlow::getBizType, bizType).eq(WalletFlow::getBizNo, bizNo)
                .eq(WalletFlow::getWalletId, wallet.getId()));
        if (dup != null && dup > 0) {
            return;
        }
        int balance = wallet.getBalance() == null ? 0 : wallet.getBalance();
        int actual = Math.min(balance, amount);
        int after = balance - actual;
        wallet.setBalance(after);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletMapper.updateById(wallet);

        WalletFlow flow = new WalletFlow();
        flow.setWalletId(wallet.getId());
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setDirection(-1);
        flow.setAmount(actual);
        flow.setBalanceAfter(after);
        flow.setRemark(actual < amount
                ? remark + "（余额不足，应扣 " + amount + " 分，实扣 " + actual + " 分，欠 " + (amount - actual) + " 分）"
                : remark);
        flow.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(flow);
    }

    private void credit(int ownerType, Long ownerId, Integer amount, String bizType, String bizNo, String remark) {        if (amount == null || amount <= 0) {
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
