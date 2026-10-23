package com.lcu.delivery.modules.settle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 结算单（表 settlement） */
@TableName("settlement")
public class Settlement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long shopId;
    private Long riderId;
    private Integer goodsAmount;
    private Integer commission;
    private Integer shopIncome;
    private Integer riderIncome;
    /** 0 待结算 1 已结算 2 已撤销 */
    private Integer status;
    private LocalDateTime settleAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public Long getRiderId() {
        return riderId;
    }

    public void setRiderId(Long riderId) {
        this.riderId = riderId;
    }

    public Integer getGoodsAmount() {
        return goodsAmount;
    }

    public void setGoodsAmount(Integer goodsAmount) {
        this.goodsAmount = goodsAmount;
    }

    public Integer getCommission() {
        return commission;
    }

    public void setCommission(Integer commission) {
        this.commission = commission;
    }

    public Integer getShopIncome() {
        return shopIncome;
    }

    public void setShopIncome(Integer shopIncome) {
        this.shopIncome = shopIncome;
    }

    public Integer getRiderIncome() {
        return riderIncome;
    }

    public void setRiderIncome(Integer riderIncome) {
        this.riderIncome = riderIncome;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getSettleAt() {
        return settleAt;
    }

    public void setSettleAt(LocalDateTime settleAt) {
        this.settleAt = settleAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
