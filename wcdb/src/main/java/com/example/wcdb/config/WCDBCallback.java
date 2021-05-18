package com.example.wcdb.config;

import android.database.sqlite.SQLiteException;

import org.greenrobot.greendao.database.Database;

public abstract class WCDBCallback {
    private static final String TAG = "SupportSQLite";

    public void onConfigure(Database db) {
    }

    public abstract void onCreate(Database db);

    public abstract void onUpgrade(Database db, int oldVersion, int newVersion);

    public void onDowngrade(Database db, int oldVersion, int newVersion) {
        throw new SQLiteException("Can't downgrade database from version "
                + oldVersion + " to " + newVersion);
    }

    public void onOpen(Database db) {

    }

    public void onCorruption(Database db) {
    }
}
