package com.lcu.delivery.modules.user;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.fence.FenceService;
import com.lcu.delivery.modules.user.entity.UserAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;
    private final FenceService fenceService;

    public AddressController(AddressService addressService, FenceService fenceService) {
        this.addressService = addressService;
        this.fenceService = fenceService;
    }

    @GetMapping
    public Result<List<UserAddress>> list() {
        return Result.ok(addressService.list(UserContext.userId()));
    }

    @PostMapping
    public Result<UserAddress> create(@RequestBody @Valid AddressService.AddressReq req) {
        return Result.ok(addressService.create(UserContext.userId(), req));
    }

    @PutMapping("/{id}")
    public Result<UserAddress> update(@PathVariable Long id, @RequestBody AddressService.AddressReq req) {
        return Result.ok(addressService.update(UserContext.userId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(UserContext.userId(), id);
        return Result.ok();
    }

    public record ParseReq(@NotBlank(message = "请粘贴地址文本") String text, BigDecimal lng, BigDecimal lat) {
    }

    /** 粘贴解析：返回结构化字段 + 置信度 + 是否在围栏内，前端回填后允许用户修改。 */
    @PostMapping("/parse")
    public Result<Map<String, Object>> parse(@RequestBody @Valid ParseReq req) {
        AddressParser.Parsed parsed = AddressParser.parse(req.text());
        boolean inFence = req.lng() != null && req.lat() != null && fenceService.inside(req.lng(), req.lat());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campus", parsed.campus());
        data.put("campusName", parsed.campusName());
        data.put("building", parsed.building());
        data.put("floor", parsed.floor());
        data.put("room", parsed.room());
        data.put("detail", parsed.detail());
        data.put("phone", parsed.phone());
        data.put("confidence", parsed.confidence());
        data.put("inFence", inFence);
        data.put("needConfirm", parsed.confidence() < 0.7);
        return Result.ok(data);
    }
}
