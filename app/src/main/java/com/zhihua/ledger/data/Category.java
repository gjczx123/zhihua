package com.zhihua.ledger.data;

/** 消费分类。纯 Java，便于在 JVM 上做单元自测。 */
public final class Category {
    public static final String FOOD = "food";
    public static final String CLOTHES = "clothes";
    public static final String SHOPPING = "shopping";
    public static final String DAILY = "daily";
    public static final String TRANSPORT = "transport";
    public static final String HOME = "home";
    public static final String FUN = "fun";
    public static final String HEALTH = "health";
    public static final String STUDY = "study";
    public static final String SOCIAL = "social";
    public static final String TECH = "tech";
    public static final String OTHER = "other";

    public static final String[] KEYS = {
            FOOD, CLOTHES, SHOPPING, DAILY, TRANSPORT, HOME,
            FUN, HEALTH, STUDY, SOCIAL, TECH, OTHER
    };
    public static final String[] NAMES = {
            "吃喝", "衣服", "购物", "日用", "交通", "居住",
            "娱乐", "医疗", "学习", "人情", "科技", "其他"
    };
    public static final String[] EMOJI = {
            "🍜", "👕", "🛍️", "🧴", "🚇", "🏠",
            "🎮", "💊", "📚", "🎁", "💻", "📦"
    };
    public static final int[] COLORS = {
            0xFFFF8A3D, 0xFFE86A8A, 0xFF4C8DFF, 0xFF2BB6A3, 0xFF6C7BFF, 0xFF8B6FE8,
            0xFFF2B84B, 0xFFE05A5A, 0xFF3FAE6A, 0xFFC77DD6, 0xFF0FA3B1, 0xFF8A97A5
    };

    private Category() {
    }

    public static String name(String key) {
        int i = indexOf(key);
        return i < 0 ? NAMES[NAMES.length - 1] : NAMES[i];
    }

    public static String emoji(String key) {
        int i = indexOf(key);
        return i < 0 ? EMOJI[EMOJI.length - 1] : EMOJI[i];
    }

    public static int color(String key) {
        int i = indexOf(key);
        return i < 0 ? COLORS[COLORS.length - 1] : COLORS[i];
    }

    public static int indexOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    public static boolean valid(String key) {
        return indexOf(key) >= 0;
    }
}
