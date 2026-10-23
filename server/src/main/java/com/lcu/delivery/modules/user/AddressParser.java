package com.lcu.delivery.modules.user;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 地址粘贴解析 L1：正则抽取。识别不到再走地图解析 / 大模型兜底（见手册 3.5.3）。
 *
 * 输入示例：
 *   东校区3号宿舍楼502 张三 13800001111
 *   西区1号公寓5层503放门口
 *   聊城大学东校区二食堂2楼
 *   3号楼502
 * 思路：先把手机号、楼栋、楼层从文本里"摘掉"，剩下的 3~4 位数字大概率是房间号。
 */
public final class AddressParser {

    private static final Pattern CAMPUS_EAST = Pattern.compile("东校|东区|东苑");
    private static final Pattern CAMPUS_WEST = Pattern.compile("西校|西区|西苑");
    private static final Pattern BUILDING = Pattern.compile(
            "(\\d{1,3})\\s*(?:号\\s*)?(?:宿舍楼|宿舍|公寓|教学楼|实验楼|楼|栋|舍)"
                    + "|([\\u4e00-\\u9fa5]{2,10}(?:宿舍楼|公寓|教学楼|实验楼|图书馆|食堂|餐厅|体育馆|超市|快递点|驿站))");
    private static final Pattern FLOOR = Pattern.compile("(\\d{1,2})\\s*(?:层|F|f)|(\\d{1,2})\\s*楼(?!号)");
    private static final Pattern ROOM = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern NOISE = Pattern.compile("[,，。;；\\s]+");

    private AddressParser() {
    }

    public record Parsed(Integer campus, String campusName, String building, String floor,
                         String room, String detail, String phone, double confidence) {
    }

    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.replace('\u3000', ' ').trim();
        String work = text;

        Integer campus = null;
        if (CAMPUS_WEST.matcher(text).find()) {
            campus = 2;
        } else if (CAMPUS_EAST.matcher(text).find()) {
            campus = 1;
        }

        String phone = null;
        Matcher pm = PHONE.matcher(work);
        if (pm.find()) {
            phone = pm.group();
            work = work.replace(phone, " ");
        }

        String building = null;
        Matcher bm = BUILDING.matcher(work);
        if (bm.find()) {
            building = bm.group(1) != null ? bm.group(1) + "号楼" : bm.group(2);
            // 去掉「聊城大学东校区」这类前缀，只留楼栋/场所名
            building = building.replaceAll("^((聊城大学|东校区|西校区|东区|西区|东校|西校))+", "").trim();
            work = work.replace(bm.group(), " ");
        }

        String floor = null;
        Matcher fm = FLOOR.matcher(work);
        if (fm.find()) {
            floor = fm.group(1) != null ? fm.group(1) : fm.group(2);
            work = work.replace(fm.group(), " ");
        }

        String room = null;
        Matcher rm = ROOM.matcher(work);
        if (rm.find()) {
            room = rm.group(1);
        }

        double confidence = 0.2;
        if (campus != null) confidence += 0.25;
        if (building != null) confidence += 0.35;
        if (room != null) confidence += 0.15;
        if (floor != null) confidence += 0.05;

        String detail = NOISE.matcher(text).replaceAll(" ").trim();
        if (detail.isEmpty()) {
            detail = raw;
        }
        String campusName = campus == null ? null : (campus == 1 ? "东校区" : "西校区");
        return new Parsed(campus, campusName, building, floor, room, detail, phone,
                Math.min(0.95, Math.round(confidence * 100) / 100.0));
    }
}
