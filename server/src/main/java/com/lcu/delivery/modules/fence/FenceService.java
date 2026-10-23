package com.lcu.delivery.modules.fence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校园配送围栏：启动时把多边形加载进内存，判定用射线法，避免每次下单都查库。
 * 坐标顺序为 (经度, 纬度)，与 MySQL 中 SRID 0 的 POLYGON 一致。
 */
@Service
public class FenceService {

    private static final Logger log = LoggerFactory.getLogger(FenceService.class);
    private static final Pattern RING = Pattern.compile("\\(([^()]*)\\)");

    public record Ring(int campus, String name, double[][] points) {
    }

    private final JdbcTemplate jdbc;
    private final AtomicReference<List<Ring>> cache = new AtomicReference<>(List.of());

    public FenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        reload();
    }

    public synchronized void reload() {
        try {
            List<Ring> rings = new ArrayList<>();
            jdbc.query("SELECT campus, name, ST_AsText(polygon) AS wkt FROM fence WHERE status = 1",
                    rs -> {
                        double[][] pts = parseWkt(rs.getString("wkt"));
                        if (pts.length >= 3) {
                            rings.add(new Ring(rs.getInt("campus"), rs.getString("name"), pts));
                        }
                    });
            cache.set(List.copyOf(rings));
            log.info("围栏加载完成：{} 个多边形", rings.size());
        } catch (Exception e) {
            log.warn("围栏加载失败（可能库未初始化）：{}", e.getMessage());
        }
    }

    /** 命中返回校区（1 东 / 2 西），否则 null。 */
    public Integer locate(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return null;
        }
        double x = lng.doubleValue();
        double y = lat.doubleValue();
        for (Ring ring : cache.get()) {
            if (contains(ring.points(), x, y)) {
                return ring.campus();
            }
        }
        return null;
    }

    public boolean inside(BigDecimal lng, BigDecimal lat) {
        return locate(lng, lat) != null;
    }

    public List<Ring> rings() {
        return cache.get();
    }

    static boolean contains(double[][] poly, double x, double y) {
        boolean hit = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            double xi = poly[i][0], yi = poly[i][1];
            double xj = poly[j][0], yj = poly[j][1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
            if (intersect) {
                hit = !hit;
            }
        }
        return hit;
    }

    /** 解析 POLYGON((x y, x y, ...)) 或 MULTIPOLYGON 的所有环。 */
    static double[][] parseWkt(String wkt) {
        if (wkt == null) {
            return new double[0][];
        }
        List<double[]> pts = new ArrayList<>();
        Matcher m = RING.matcher(wkt);
        while (m.find()) {
            for (String pair : m.group(1).split(",")) {
                String[] xy = pair.trim().split("\\s+");
                if (xy.length >= 2) {
                    pts.add(new double[]{Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
                }
            }
        }
        return pts.toArray(new double[0][]);
    }
}
