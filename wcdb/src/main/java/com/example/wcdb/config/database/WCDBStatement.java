package com.example.wcdb.config.database;

import com.tencent.wcdb.database.SQLiteStatement;

import org.greenrobot.greendao.database.DatabaseStatement;

public class WCDBStatement implements DatabaseStatement {
    private final SQLiteStatement mDelegate;

    public WCDBStatement(SQLiteStatement delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public void execute() {
        mDelegate.execute();
    }

    @Override
    public long simpleQueryForLong() {
        return mDelegate.simpleQueryForLong();
    }

    @Override
    public void bindNull(int index) {
        mDelegate.bindNull(index);
    }

    @Override
    public long executeInsert() {
        return mDelegate.executeInsert();
    }

    @Override
    public void bindString(int index, String value) {
        mDelegate.bindString(index, value);
    }

    @Override
    public void bindBlob(int index, byte[] value) {
        mDelegate.bindBlob(index, value);
    }

    @Override
    public void bindLong(int index, long value) {
        mDelegate.bindLong(index, value);
    }

    @Override
    public void clearBindings() {
        mDelegate.clearBindings();
    }

    @Override
    public void bindDouble(int index, double value) {
        mDelegate.bindDouble(index, value);
    }

    @Override
    public void close() {
        mDelegate.close();
    }

    @Override
    public Object getRawStatement() {
        return mDelegate;
    }
}
