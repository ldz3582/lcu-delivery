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
 *   山东省聊城市东昌府区聊城大学东校区紫藤公寓3号楼5层502室
 * 思路：先把手机号、楼栋、楼层从文本里"摘掉"，剩下的 3~4 位数字大概率是房间号。
 *
 * 注意两个历史坑（本轮修掉）：
 * 1) 楼栋正则原来允许 2~10 个汉字，遇到「省市区」长地址会把行政区划一起吞进来，
 *    导致楼栋变成「市东昌府区聊城大学东校区紫藤公寓」这类污染值；
 * 2) detail 用的是原始文本，手机号没有被摘掉，等于把隐私又回显了一遍。
 */
public final class AddressParser {

    private static final Pattern CAMPUS_EAST = Pattern.compile("东校|东区|东苑");
    private static final Pattern CAMPUS_WEST = Pattern.compile("西校|西区|西苑");
    /** 数字楼栋：3号宿舍楼 / 12栋 / 5号楼 */
    private static final Pattern BUILDING_NUM = Pattern.compile(
            "(\\d{1,3})\\s*(?:号\\s*)?(?:宿舍楼|宿舍|公寓|教学楼|实验楼|楼|栋|舍)");
    /** 命名楼栋：紫藤公寓 / 玉兰苑 / 图书馆 / 二食堂（限定 2~6 字，避免吞掉行政区划） */
    private static final Pattern BUILDING_NAME = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6})(?:宿舍楼|公寓|教学楼|实验楼|图书馆|食堂|餐厅|体育馆|超市|快递点|驿站)");
    /** 命名楼栋 + 楼层：二食堂2楼 / 图书馆3层（这里的数字是楼层，不是楼栋号） */
    private static final Pattern NAME_THEN_FLOOR = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6}(?:宿舍楼|公寓|教学楼|实验楼|图书馆|食堂|餐厅|体育馆|超市|快递点|驿站))"
                    + "\\s*(?:第\\s*)?(\\d{1,2})\\s*(?:层|楼|F|f)");
    private static final Pattern FLOOR = Pattern.compile("(\\d{1,2})\\s*(?:层|F|f)|第\\s*(\\d{1,2})\\s*层");
    private static final Pattern ROOM = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)\\s*(?:室|房|号房)?");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern NOISE = Pattern.compile("[,，。;；\\s]+");
    /** 行政区划/泛化前缀：命名楼栋里出现这些词就往后截断 */
    private static final Pattern ADMIN_PREFIX = Pattern.compile(
            "^.*?(?:省|市|区|县|镇|乡|街道|路|街|号|大学|校区|东校|西校|东区|西区|东苑|西苑|苑)+");

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
        String floor = null;
        // ①「二食堂2楼」：数字是楼层，楼栋是前面的名字，别把 2楼 当成 2 号楼
        Matcher nf = NAME_THEN_FLOOR.matcher(work);
        if (nf.find()) {
            String name = nf.group(1);
            String trimmed = ADMIN_PREFIX.matcher(name).replaceFirst("");
            building = (trimmed.isEmpty() || trimmed.length() > 6) ? name : trimmed;
            floor = nf.group(2);
            work = work.replace(nf.group(), " ");
        } else {
            // ② 数字楼栋（最可靠），③ 命名楼栋
            Matcher bm = BUILDING_NUM.matcher(work);
            if (bm.find()) {
                building = bm.group(1) + "号楼";
                work = work.replace(bm.group(), " ");
            } else {
                Matcher nm = BUILDING_NAME.matcher(work);
                if (nm.find()) {
                    String name = nm.group(1);
                    String trimmed = ADMIN_PREFIX.matcher(name).replaceFirst("");
                    building = (trimmed.isEmpty() || trimmed.length() > 6) ? name : trimmed;
                    work = work.replace(nm.group(), " ");
                }
            }
        }

        if (floor == null) {
            Matcher fm = FLOOR.matcher(work);
            if (fm.find()) {
                floor = fm.group(1) != null ? fm.group(1) : fm.group(2);
                work = work.replace(fm.group(), " ");
            }
        }

        String room = null;
        Matcher rm = ROOM.matcher(work);
        if (rm.find()) {
            room = rm.group(1);
        }

        double confidence = 0.2;
        if (campus != null) {
            confidence += 0.25;
        }
        if (building != null) {
            confidence += 0.35;
        }
        if (room != null) {
            confidence += 0.15;
        }
        if (floor != null) {
            confidence += 0.05;
        }
        // 楼栋名过长说明很可能把整段描述当成了楼栋，降置信度提示用户核对
        if (building != null && building.length() > 8) {
            confidence -= 0.15;
        }

        // 详细地址：保留用户原文（去掉手机号，避免隐私回显），压缩空白；
        // 不要用 work（已把楼栋/楼层/房号摘掉），否则编辑页回填会丢门牌信息
        String detail = NOISE.matcher(phone == null ? text : text.replace(phone, " ")).replaceAll(" ").trim();
        if (detail.isEmpty()) {
            detail = text;
        }
        String campusName = campus == null ? null : (campus == 1 ? "东校区" : "西校区");
        return new Parsed(campus, campusName, building, floor, room, detail, phone,
                Math.max(0.1, Math.min(0.95, Math.round(confidence * 100) / 100.0)));
    }
}
