package com.lcu.delivery.modules.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.fence.FenceService;
import com.lcu.delivery.modules.user.entity.UserAddress;
import com.lcu.delivery.modules.user.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressService {

    private final UserAddressMapper mapper;
    private final FenceService fenceService;

    public AddressService(UserAddressMapper mapper, FenceService fenceService) {
        this.mapper = mapper;
        this.fenceService = fenceService;
    }

    public record AddressReq(Integer campus, String detail, String building, String floor, String room,
                             String remark, BigDecimal lng, BigDecimal lat, Integer isDefault) {
    }

    public List<UserAddress> list(Long userId) {
        return mapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .isNull(UserAddress::getDeletedAt)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    public UserAddress get(Long userId, Long id) {
        UserAddress address = mapper.selectById(id);
        if (address == null || !userId.equals(address.getUserId()) || address.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    @Transactional
    public UserAddress create(Long userId, AddressReq req) {
        if (req.detail() == null || req.detail().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "详细地址不能为空");
        }
        if (req.isDefault() != null && req.isDefault() == 1) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        apply(address, req);
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        mapper.insert(address);
        return address;
    }

    @Transactional
    public UserAddress update(Long userId, Long id, AddressReq req) {
        UserAddress address = get(userId, id);
        if (req.isDefault() != null && req.isDefault() == 1) {
            clearDefault(userId);
        }
        apply(address, req);
        address.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(address);
        return address;
    }

    public void delete(Long userId, Long id) {
        UserAddress address = get(userId, id);
        address.setDeletedAt(LocalDateTime.now());
        mapper.updateById(address);
    }

    private void apply(UserAddress address, AddressReq req) {
        address.setCampus(req.campus());
        address.setDetail(req.detail());
        address.setBuilding(req.building());
        address.setFloor(req.floor());
        address.setRoom(req.room());
        address.setRemark(req.remark());
        address.setLng(req.lng());
        address.setLat(req.lat());
        if (req.lng() != null && req.lat() != null) {
            Integer campus = fenceService.locate(req.lng(), req.lat());
            address.setInFence(campus == null ? 0 : 1);
            if (campus != null && address.getCampus() == null) {
                address.setCampus(campus);
            }
        } else {
            address.setInFence(0);
        }
        address.setParseSource(1);
        address.setIsDefault(req.isDefault() == null ? 0 : req.isDefault());
    }

    private void clearDefault(Long userId) {
        mapper.update(null, Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }
}
