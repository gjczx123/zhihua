package com.zhihua.ledger.export;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 导出 CSV（带 BOM，Excel 直接打开不乱码）。 */
public final class CsvExporter {
    private CsvExporter() {
    }

    public static void write(OutputStream out, List<Transaction> list) throws Exception {
        OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('\uFEFF');
        w.write("时间,金额(元),商户,平台,分类,备注,来源,卡尾号,是否冷静提醒,原始通知\r\n");
        for (Transaction t : list) {
            w.write(csv(DateX.dateTime(t.occurredAt)) + ",");
            w.write(Money.formatPlain(t.amountCents) + ",");
            w.write(csv(t.merchant) + ",");
            w.write(csv(t.platform) + ",");
            w.write(csv(Category.name(t.category)) + ",");
            w.write(csv(t.note) + ",");
            w.write(csv(t.sourceLabel()) + ",");
            w.write(csv(t.cardTail) + ",");
            w.write(csv(t.impulse == 0 ? "否" : t.impulse == 1 ? "确认后购买" : "冷静后仍购买") + ",");
            w.write(csv(t.rawText.replace('\n', ' ')));
            w.write("\r\n");
        }
        w.flush();
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ");
        if (v.contains(",") || v.contains("\"") || v.contains(";")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
