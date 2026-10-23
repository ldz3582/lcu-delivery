package com.lcu.delivery.modules.user;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 地址粘贴解析 L1：正则抽取。识别不到再走地图解析 / 大模型兜底（见手册 3.5.3）。
 */
public final class AddressParser {

    private static final Pattern CAMPUS_EAST = Pattern.compile("东校|东区|东苑");
    private static final Pattern CAMPUS_WEST = Pattern.compile("西校|西区|西苑");
    private static final Pattern BUILDING = Pattern.compile(
            "(\\d{1,3})\\s*(?:号\\s*)?(?:宿舍楼|宿舍|公寓|教学楼|实验楼|楼|栋|舍|馆|堂|厅)"
                    + "|([\\u4e00-\\u9fa5]{2,8}(?:宿舍楼|公寓|教学楼|实验楼|图书馆|食堂|餐厅|体育馆|超市|快递点|驿站))");
    private static final Pattern FLOOR = Pattern.compile("(\\d{1,2})\\s*(?:层|F|f)|(\\d{1,2})\\s*楼(?!号)");
    private static final Pattern ROOM = Pattern.compile("(\\d{3,4})\\s*(?:室|号房|寝|房间)|([A-Za-z]?\\d{3,4})\\s*$");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    private AddressParser() {
    }

    public record Parsed(Integer campus, String campusName, String building, String floor,
                         String room, String detail, String phone, double confidence) {
    }

    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.replace('\u3000', ' ').trim();
        Integer campus = null;
        if (CAMPUS_WEST.matcher(text).find()) {
            campus = 2;
        } else if (CAMPUS_EAST.matcher(text).find()) {
            campus = 1;
        }

        String phone = null;
        Matcher pm = PHONE.matcher(text);
        if (pm.find()) {
            phone = pm.group();
            text = text.replace(phone, "");
        }

        String building = null;
        Matcher bm = BUILDING.matcher(text);
        if (bm.find()) {
            building = bm.group(1) != null ? bm.group(1) + "号楼" : bm.group(2);
        }

        String floor = null;
        Matcher fm = FLOOR.matcher(text);
        if (fm.find()) {
            floor = fm.group(1) != null ? fm.group(1) : fm.group(2);
        }

        String room = null;
        Matcher rm = ROOM.matcher(text);
        if (rm.find()) {
            room = rm.group(1) != null ? rm.group(1) : rm.group(2);
        }

        double confidence = 0.2;
        if (campus != null) confidence += 0.25;
        if (building != null) confidence += 0.35;
        if (room != null) confidence += 0.15;
        if (floor != null) confidence += 0.05;

        String detail = text.replaceAll("[,，。;；\\s]+", " ").trim();
        if (detail.isEmpty()) {
            detail = raw;
        }
        String campusName = campus == null ? null : (campus == 1 ? "东校区" : "西校区");
        return new Parsed(campus, campusName, building, floor, room, detail, phone,
                Math.min(0.95, Math.round(confidence * 100) / 100.0));
    }
}
