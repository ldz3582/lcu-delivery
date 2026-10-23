package com.lcu.delivery.modules.fence;

import com.lcu.delivery.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开的围栏接口：小程序地址页要在地图上把可配送范围画出来，
 * 只返回多边形顶点（经度、纬度），不含任何敏感信息。
 */
@RestController
@RequestMapping("/api/fence")
public class FenceController {

    private final FenceService fenceService;

    public FenceController(FenceService fenceService) {
        this.fenceService = fenceService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FenceService.Ring ring : fenceService.rings()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campus", ring.campus());
            item.put("name", ring.name());
            List<Map<String, Double>> pts = new ArrayList<>();
            for (double[] p : ring.points()) {
                Map<String, Double> xy = new LinkedHashMap<>();
                xy.put("lng", p[0]);
                xy.put("lat", p[1]);
                pts.add(xy);
            }
            item.put("points", pts);
            out.add(item);
        }
        return Result.ok(out);
    }
}
