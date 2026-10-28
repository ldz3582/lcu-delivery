package com.lcu.delivery.modules.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 配送任务（表 delivery_task） */
@TableName("delivery_task")
public class DeliveryTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long riderId;
    /** 1 抢单 2 派单 */
    private Integer assignType;
    /** 0 待接 1 已接 2 已取餐 3 已送达 4 已取消 */
    private Integer status;
    private String pickupPhoto;
    private String deliverPhoto;
    private LocalDateTime grabAt;
    private LocalDateTime pickupAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getRiderId() {
        return riderId;
    }

    public void setRiderId(Long riderId) {
        this.riderId = riderId;
    }

    public Integer getAssignType() {
        return assignType;
    }

    public void setAssignType(Integer assignType) {
        this.assignType = assignType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPickupPhoto() {
        return pickupPhoto;
    }

    public void setPickupPhoto(String pickupPhoto) {
        this.pickupPhoto = pickupPhoto;
    }

    public String getDeliverPhoto() {
        return deliverPhoto;
    }

    public void setDeliverPhoto(String deliverPhoto) {
        this.deliverPhoto = deliverPhoto;
    }

    public LocalDateTime getGrabAt() {
        return grabAt;
    }

    public void setGrabAt(LocalDateTime grabAt) {
        this.grabAt = grabAt;
    }

    public LocalDateTime getPickupAt() {
        return pickupAt;
    }

    public void setPickupAt(LocalDateTime pickupAt) {
        this.pickupAt = pickupAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
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
