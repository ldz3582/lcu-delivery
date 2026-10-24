package com.lcu.delivery.job;

import com.lcu.delivery.modules.delivery.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 智能派单：补派待取餐的单 + 收回超时未确认的派单（每 30 秒）。 */
@Component
public class DispatchJob {

    private static final Logger log = LoggerFactory.getLogger(DispatchJob.class);

    private final DispatchService dispatchService;

    public DispatchJob(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void run() {
        try {
            int expired = dispatchService.expireStale();
            int assigned = dispatchService.dispatchPending();
            if (expired > 0 || assigned > 0) {
                log.info("派单任务：回收 {} 单，新派 {} 单", expired, assigned);
            }
        } catch (Exception e) {
            log.warn("派单任务异常：{}", e.getMessage());
        }
    }
}
