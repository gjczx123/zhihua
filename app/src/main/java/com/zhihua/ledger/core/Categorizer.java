package com.zhihua.ledger.core;

import com.zhihua.ledger.data.Category;

import java.util.Locale;

/** 基于商户/平台/通知关键词的本地分类器，不联网。 */
public final class Categorizer {

    public static final class Result {
        public final String category;
        public final double confidence;
        public final String reason;

        public Result(String category, double confidence, String reason) {
            this.category = category;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    private static final String[][] RULES = {
            {Category.FUN, "抖币", "游戏充值", "游戏", "电影", "演出", "剧本杀", "ktv", "网吧",
                    "腾讯视频", "爱奇艺", "哔哩哔哩", "网易云", "视频会员", "音乐会员", "steam"},
            {Category.FOOD, "美团外卖", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸", "蜜雪",
                    "喜茶", "奈雪", "海底捞", "餐饮", "餐厅", "饭店", "火锅", "烧烤", "咖啡",
                    "奶茶", "面包", "蛋糕", "小吃", "食堂", "早餐", "午餐", "晚餐", "外卖",
                    "买菜", "盒马", "叮咚", "多多买菜", "快餐", "面馆", "米粉", "麻辣烫",
                    "炸鸡", "汉堡", "披萨", "寿司", "水果", "生鲜", "菜市场"},
            {Category.CLOTHES, "优衣库", "服装", "衣服", "鞋", "靴", "帽", "裤", "裙", "内衣",
                    "袜", "耐克", "阿迪", "李宁", "安踏", "太平鸟", "森马", "海澜", "唯品会",
                    "服饰", "鞋服", "户外", "箱包"},
            {Category.TRANSPORT, "滴滴", "高德打车", "出租车", "地铁", "公交", "加油", "中石化",
                    "中石油", "停车", "高速", "12306", "铁路", "机票", "航空", "单车", "哈啰",
                    "网约车", "充电桩", "洗车"},
            {Category.HOME, "房租", "租金", "物业", "水费", "电费", "燃气", "供暖", "宽带",
                    "维修", "装修", "家政"},
            {Category.HEALTH, "医院", "药房", "药店", "诊所", "体检", "医保", "挂号", "牙科",
                    "眼科", "口腔", "药品"},
            {Category.STUDY, "书店", "图书", "课程", "学费", "培训", "教育", "知识", "考试",
                    "文具", "网课"},
            {Category.SOCIAL, "红包", "礼物", "婚礼", "份子", "捐赠", "公益"},
            {Category.TECH, "openai", "chatgpt", "claude", "gemini", "api", "云服务", "服务器",
                    "域名", "软件", "订阅", "adobe", "microsoft", "office", "icloud", "apple",
                    "iphone", "app store", "github", "开发者", "开发", "科技", "数码", "电脑",
                    "笔记本", "硬件", "显卡", "键盘", "鼠标", "显示器", "人工智能", "华为",
                    "小米", "编程", "代码托管"},
            {Category.DAILY, "超市", "便利店", "沃尔玛", "永辉", "家乐福", "山姆", "日用",
                    "百货", "纸巾", "洗衣", "清洁", "家居", "五金", "母婴", "牙膏", "洗发",
                    "屈臣氏", "名创优品", "话费", "流量"},
            {Category.SHOPPING, "拼多多", "淘宝", "天猫", "京东", "抖音", "商城", "购物", "电商",
                    "小红书", "得物", "闲鱼", "苏宁", "国美", "家电", "化妆品", "护肤",
                    "彩妆", "香水", "眼镜", "商贸", "贸易"}
    };

    private Categorizer() {
    }

    public static Result categorize(String merchant, String platform, String raw, String learnedCategory) {
        if (learnedCategory != null && Category.valid(learnedCategory)) {
            return new Result(learnedCategory, 1.0, "已记住你对这个商户的分类");
        }
        String m = lower(merchant);
        String p = lower(platform);
        String r = lower(raw);

        // “抖音”既是平台也是娱乐入口：页面出现抖币/游戏充值时优先算娱乐。
        if (r.contains("抖币") || r.contains("游戏充值") || r.contains("视频会员")
                || r.contains("直播充值")) {
            return new Result(Category.FUN, 0.88, "识别到充值/会员关键词");
        }

        // 先看商户名，再看待分类文本；平台放在最后，避免“支付宝”盖过具体商户。
        for (String[] rule : RULES) {
            String hit = firstHit(m, rule);
            if (hit != null) return new Result(rule[0], 0.92, "识别到「" + hit + "」");
        }
        for (String[] rule : RULES) {
            String hit = firstHit(r, rule);
            if (hit != null) return new Result(rule[0], 0.82, "通知里提到「" + hit + "」");
        }
        for (String[] rule : RULES) {
            String hit = firstHit(p, rule);
            if (hit != null) return new Result(rule[0], 0.76, "消费平台是「" + hit + "」");
        }

        if (p.contains("美团") || p.contains("饿了么")) {
            return new Result(Category.FOOD, 0.7, "外卖平台");
        }
        if (p.contains("拼多多") || p.contains("淘宝") || p.contains("京东")
                || p.contains("抖音") || p.contains("唯品会")) {
            return new Result(Category.SHOPPING, 0.7, "购物平台");
        }
        if (p.contains("滴滴") || p.contains("12306")) {
            return new Result(Category.TRANSPORT, 0.7, "出行平台");
        }
        if (p.contains("微信") || p.contains("支付宝") || p.contains("财付通")) {
            return new Result(Category.OTHER, 0.32, "只看到支付渠道，没看到具体收款方");
        }
        return new Result(Category.OTHER, 0.3, "暂时没有匹配到分类");
    }

    private static String firstHit(String text, String[] rule) {
        for (int i = 1; i < rule.length; i++) {
            if (text.contains(rule[i])) return rule[i];
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.CHINA);
    }
}
