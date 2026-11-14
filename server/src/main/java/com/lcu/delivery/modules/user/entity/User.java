package com.lcu.delivery.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户（表 user） */
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    /** 微信 unionid：同一个小程序/公众号下的同一自然人 */
    private String unionid;
    /** 公众号网页授权 openid（与小程序 openid 不是同一个值） */
    private String mpOpenid;
    private String nickname;
    private String avatar;
    private String phoneMask;
    /** 手机号密文（AES-GCM），用于「加密存储」要求 */
    private String phoneEnc;
    /** 学号/工号密文（AES-GCM），identity_status=1 表示已核验 */
    private String stuNoEnc;
    /** 学号/工号 SHA-256，用于查重 */
    private String stuNoHash;
    /** 0 未核验 / 1 已核验 */
    private Integer identityStatus;
    /** USER / MERCHANT / RIDER / ADMIN */
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public String getMpOpenid() {
        return mpOpenid;
    }

    public void setMpOpenid(String mpOpenid) {
        this.mpOpenid = mpOpenid;
    }


    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getPhoneMask() {
        return phoneMask;
    }

    public String getStuNoEnc() {
        return stuNoEnc;
    }

    public void setStuNoEnc(String stuNoEnc) {
        this.stuNoEnc = stuNoEnc;
    }

    public String getStuNoHash() {
        return stuNoHash;
    }

    public void setStuNoHash(String stuNoHash) {
        this.stuNoHash = stuNoHash;
    }

    public Integer getIdentityStatus() {
        return identityStatus;
    }

    public void setIdentityStatus(Integer identityStatus) {
        this.identityStatus = identityStatus;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public void setPhoneEnc(String phoneEnc) {
        this.phoneEnc = phoneEnc;
    }

    public void setPhoneMask(String phoneMask) {
        this.phoneMask = phoneMask;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

}
