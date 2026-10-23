package com.lcu.delivery.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 订单（表 orders） */
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long shopId;
    private Long riderId;
    /** 0 待支付 10 待接单 20 待出餐 30 待取餐 40 配送中 50 已完成 60 已取消 70 售后中 80 已退款 */
    private Integer status;
    private Integer campus;
    private String addressJson;
    private BigDecimal lng;
    private BigDecimal lat;
    private Integer goodsAmount;
    private Integer packFee;
    private Integer deliveryFee;
    private Integer discountAmount;
    private Integer payAmount;
    private Integer commission;
    private Integer riderIncome;
    private Integer shopIncome;
    private Long couponId;
    private String remark;
    private LocalDateTime expectTime;
    private LocalDateTime payExpireAt;
    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime finishedAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCampus() {
        return campus;
    }

    public void setCampus(Integer campus) {
        this.campus = campus;
    }

    public String getAddressJson() {
        return addressJson;
    }

    public void setAddressJson(String addressJson) {
        this.addressJson = addressJson;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public Integer getGoodsAmount() {
        return goodsAmount;
    }

    public void setGoodsAmount(Integer goodsAmount) {
        this.goodsAmount = goodsAmount;
    }

    public Integer getPackFee() {
        return packFee;
    }

    public void setPackFee(Integer packFee) {
        this.packFee = packFee;
    }

    public Integer getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(Integer deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public Integer getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getCommission() {
        return commission;
    }

    public void setCommission(Integer commission) {
        this.commission = commission;
    }

    public Integer getRiderIncome() {
        return riderIncome;
    }

    public void setRiderIncome(Integer riderIncome) {
        this.riderIncome = riderIncome;
    }

    public Integer getShopIncome() {
        return shopIncome;
    }

    public void setShopIncome(Integer shopIncome) {
        this.shopIncome = shopIncome;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getExpectTime() {
        return expectTime;
    }

    public void setExpectTime(LocalDateTime expectTime) {
        this.expectTime = expectTime;
    }

    public LocalDateTime getPayExpireAt() {
        return payExpireAt;
    }

    public void setPayExpireAt(LocalDateTime payExpireAt) {
        this.payExpireAt = payExpireAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public LocalDateTime getPickedAt() {
        return pickedAt;
    }

    public void setPickedAt(LocalDateTime pickedAt) {
        this.pickedAt = pickedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
