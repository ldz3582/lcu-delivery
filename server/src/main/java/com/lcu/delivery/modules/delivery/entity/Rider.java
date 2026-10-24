package com.lcu.delivery.modules.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 骑手（表 rider） */
@TableName("rider")
public class Rider {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String realNameEnc;
    private String idCardEnc;
    private String stuNoEnc;
    private String stuNoHash;
    private String phoneMask;
    /** 身份证正面/反面、校园卡照片 */
    private String idCardFront;
    private String idCardBack;
    private String studentCard;
    /** 电子兼职协议存证与签署时间 */
    private String agreementUrl;
    private LocalDateTime agreementSignedAt;
    /** 意外险保单号 */
    private String insuranceNo;
    /** 审核驳回原因 */
    private String rejectReason;
    /** 0 待审 1 通过 2 驳回 */
    private Integer auditStatus;
    /** 0 离线 1 接单中 2 忙碌 */
    private Integer workStatus;
    private Integer deposit;
    private Integer maxConcurrent;
    private BigDecimal score;
    private Integer totalOrders;
    private Integer timeoutOrders;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRealNameEnc() {
        return realNameEnc;
    }

    public void setRealNameEnc(String realNameEnc) {
        this.realNameEnc = realNameEnc;
    }

    public String getIdCardEnc() {
        return idCardEnc;
    }

    public void setIdCardEnc(String idCardEnc) {
        this.idCardEnc = idCardEnc;
    }

    public String getStuNoEnc() {
        return stuNoEnc;
    }

    public void setStuNoEnc(String stuNoEnc) {
        this.stuNoEnc = stuNoEnc;
    }

    public String getPhoneMask() {
        return phoneMask;
    }

    public void setPhoneMask(String phoneMask) {
        this.phoneMask = phoneMask;
    }

    public String getStuNoHash() {
        return stuNoHash;
    }

    public void setStuNoHash(String stuNoHash) {
        this.stuNoHash = stuNoHash;
    }

    public String getIdCardFront() {
        return idCardFront;
    }

    public void setIdCardFront(String idCardFront) {
        this.idCardFront = idCardFront;
    }

    public String getIdCardBack() {
        return idCardBack;
    }

    public void setIdCardBack(String idCardBack) {
        this.idCardBack = idCardBack;
    }

    public String getStudentCard() {
        return studentCard;
    }

    public void setStudentCard(String studentCard) {
        this.studentCard = studentCard;
    }

    public String getAgreementUrl() {
        return agreementUrl;
    }

    public void setAgreementUrl(String agreementUrl) {
        this.agreementUrl = agreementUrl;
    }

    public LocalDateTime getAgreementSignedAt() {
        return agreementSignedAt;
    }

    public void setAgreementSignedAt(LocalDateTime agreementSignedAt) {
        this.agreementSignedAt = agreementSignedAt;
    }

    public String getInsuranceNo() {
        return insuranceNo;
    }

    public void setInsuranceNo(String insuranceNo) {
        this.insuranceNo = insuranceNo;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    public Integer getWorkStatus() {
        return workStatus;
    }

    public void setWorkStatus(Integer workStatus) {
        this.workStatus = workStatus;
    }

    public Integer getDeposit() {
        return deposit;
    }

    public void setDeposit(Integer deposit) {
        this.deposit = deposit;
    }

    public Integer getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(Integer maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Integer totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Integer getTimeoutOrders() {
        return timeoutOrders;
    }

    public void setTimeoutOrders(Integer timeoutOrders) {
        this.timeoutOrders = timeoutOrders;
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
