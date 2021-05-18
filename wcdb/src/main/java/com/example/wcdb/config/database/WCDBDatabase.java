/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.wcdb.config.database;

import android.database.Cursor;
import android.database.SQLException;

import com.tencent.wcdb.database.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

/**
 * Delegates all calls to an implementation of {@link SQLiteDatabase}.
 *
 * @hide
 */
public class WCDBDatabase implements Database {
    private final SQLiteDatabase mDelegate;

    public WCDBDatabase(SQLiteDatabase delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public boolean isOpen() {
        return mDelegate.isOpen();
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return mDelegate.rawQuery(sql, selectionArgs);
    }

    @Override
    public Object getRawDatabase() {
        return mDelegate;
    }

    @Override
    public void execSQL(String sql) throws SQLException {
        mDelegate.execSQL(sql);
    }

    @Override
    public void beginTransaction() {
        mDelegate.beginTransaction();
    }

    @Override
    public void endTransaction() {
        mDelegate.endTransaction();
    }

    @Override
    public boolean inTransaction() {
        return mDelegate.inTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        mDelegate.setTransactionSuccessful();
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        mDelegate.execSQL(sql, bindArgs);
    }

    @Override
    public DatabaseStatement compileStatement(String sql) {
        return new WCDBStatement(mDelegate.compileStatement(sql));
    }

    @Override
    public boolean isDbLockedByCurrentThread() {
        return mDelegate.isDbLockedByCurrentThread();
    }

    @Override
    public void close() {
        mDelegate.close();
    }
}
