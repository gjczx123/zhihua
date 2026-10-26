package com.zhihua.ledger.ui;

import com.zhihua.ledger.data.Transaction;

public interface Host {
    void openAdd();

    void openEdit(Transaction t);

    void selectTab(int index);

    void openSettings();

    void refreshAll();

    void exportCsv();
}
