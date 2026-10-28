package com.lcu.delivery.job;

import com.lcu.delivery.config.AppProperties;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.settle.SettlementService;
import com.lcu.delivery.modules.settle.entity.Settlement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** T+N 到期结算（每 5 分钟扫一次）。 */
@Component
public class SettleJob {

    private static final Logger log = LoggerFactory.getLogger(SettleJob.class);

    private final SettlementService settlementService;
    private final AppProperties app;
    private final SysConfigService config;

    public SettleJob(SettlementService settlementService, AppProperties app, SysConfigService config) {
        this.settlementService = settlementService;
        this.app = app;
        this.config = config;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void settle() {
        // T+N 冻结天数以后台配置为准（settle.freeze_days），没有配置才回落启动参数
        int freezeDays = Math.max(0, config.getInt("settle.freeze_days", app.settleFreezeDays()));
        LocalDateTime deadline = LocalDateTime.now().minusDays(freezeDays);
        List<Settlement> due = settlementService.dueList(deadline);
        if (!due.isEmpty()) {
            log.info("待结算 {} 单（冻结 {} 天，截止 {}）", due.size(), freezeDays, deadline);
        }
        for (Settlement s : due) {
            try {
                settlementService.settle(s);
                log.info("结算单 {} 已结算", s.getId());
            } catch (Exception e) {
                log.error("结算失败 {}", s.getId(), e);
            }
        }
    }
}
