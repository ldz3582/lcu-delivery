package com.lcu.delivery.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 订单状态流水（表 order_status_log） */
@TableName("order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer fromStatus;
    private Integer toStatus;
    /** 1 用户 2 商家 3 骑手 4 系统 5 管理员 */
    private Integer operatorType;
    private Long operatorId;
    private String remark;
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

    public Integer getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(Integer fromStatus) {
        this.fromStatus = fromStatus;
    }

    public Integer getToStatus() {
        return toStatus;
    }

    public void setToStatus(Integer toStatus) {
        this.toStatus = toStatus;
    }

    public Integer getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType) {
        this.operatorType = operatorType;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
