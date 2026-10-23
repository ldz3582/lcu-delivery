package com.lcu.delivery.job;

import com.lcu.delivery.config.AppProperties;
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

    public SettleJob(SettlementService settlementService, AppProperties app) {
        this.settlementService = settlementService;
        this.app = app;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void settle() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(app.settleFreezeDays());
        List<Settlement> due = settlementService.dueList(deadline);
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
