package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.admin.entity.SysConfig;
import com.lcu.delivery.modules.settle.entity.Settlement;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SysConfigService configService;

    public AdminController(AdminService adminService, SysConfigService configService) {
        this.adminService = adminService;
        this.configService = configService;
    }

    public record LoginReq(String username, String password) {
    }

    @PostMapping("/login")
    public Result<AdminService.LoginResp> login(@RequestBody LoginReq req) {
        return Result.ok(adminService.login(req.username(), req.password()));
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(adminService.overview());
    }

    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.users(keyword, page, size));
    }

    @GetMapping("/shops")
    public Result<PageResult<Map<String, Object>>> shops(@RequestParam(required = false) Integer auditStatus,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.shops(auditStatus, page, size));
    }

    public record AuditReq(boolean pass, String reason) {
    }

    @PostMapping("/shops/{id}/audit")
    public Result<Void> auditShop(@PathVariable Long id, @RequestBody AuditReq req) {
        adminService.auditShop(id, req.pass(), req.reason());
        return Result.ok();
    }

    @GetMapping("/riders")
    public Result<PageResult<Map<String, Object>>> riders(@RequestParam(required = false) Integer auditStatus,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.riders(auditStatus, page, size));
    }

    @PostMapping("/riders/{id}/audit")
    public Result<Void> auditRider(@PathVariable Long id, @RequestBody AuditReq req) {
        adminService.auditRider(id, req.pass());
        return Result.ok();
    }

    @GetMapping("/orders")
    public Result<PageResult<Map<String, Object>>> orders(@RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.orders(status, keyword, page, size));
    }

    @GetMapping("/settlements")
    public Result<PageResult<Settlement>> settlements(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.settlements(page, size));
    }

    /** 手动结算一单（演示/应急；正常 T+N 自动结算） */
    @PostMapping("/settlements/{id}/settle")
    public Result<Void> settleOne(@PathVariable Long id) {
        adminService.settleOne(id);
        return Result.ok();
    }

    @GetMapping("/fence")
    public Result<List<Map<String, Object>>> fence() {
        return Result.ok(adminService.fences());
    }

    public record FenceReq(Integer campus, String name, List<List<Double>> points) {
    }

    @PutMapping("/fence")
    public Result<Void> saveFence(@RequestBody FenceReq req) {
        adminService.saveFence(req.campus(), req.name(), req.points());
        return Result.ok();
    }

    @GetMapping("/configs")
    public Result<List<SysConfig>> configs() {
        return Result.ok(configService.listAll());
    }

    public record ConfigReq(String key, String value) {
    }

    @PutMapping("/configs")
    public Result<Void> updateConfig(@RequestBody ConfigReq req) {
        configService.update(req.key(), req.value(), UserContext.userId());
        return Result.ok();
    }
}
