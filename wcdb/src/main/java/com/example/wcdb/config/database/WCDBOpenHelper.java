package com.example.wcdb.config.database;

import android.content.Context;
import android.util.Log;

import com.example.wcdb.RepairableDatabase;
import com.example.wcdb.config.WCDBCallback;
import com.example.wcdb.config.DbConfig;
import com.example.wcdb.entity.UserDao;
import com.tencent.wcdb.database.SQLiteCipherSpec;
import com.tencent.wcdb.database.SQLiteOpenHelper;

import org.greenrobot.greendao.database.Database;

public class WCDBOpenHelper {
    private final OpenHelper mDelegate;

    public WCDBOpenHelper(Context context, String name, byte[] passphrase, SQLiteCipherSpec cipherSpec,
                   WCDBCallback callback) {
        mDelegate = createDelegate(context, name, passphrase, cipherSpec, callback);
    }

    private OpenHelper createDelegate(Context context, String name, byte[] passphrase,
                                      SQLiteCipherSpec cipherSpec, WCDBCallback callback) {
        final WCDBDatabase[] dbRef = new WCDBDatabase[1];
        return new OpenHelper(context, name, dbRef, passphrase, cipherSpec, callback);
    }

    public Database getWritableDb() {
        return mDelegate.getWritableGDDatabase();
    }

    public Database getReadableDb() {
        return mDelegate.getReadableGDDatabase();
    }

    static class OpenHelper extends SQLiteOpenHelper {
        private static final String TAG = WCDBOpenHelper.class.getName();
        /**
         * This is used as an Object reference so that we can access the wrapped database inside
         * the constructor. SQLiteOpenHelper requires the error handler to be passed in the
         * constructor.
         */
        final WCDBDatabase[] mDbRef;
        final WCDBCallback mCallback;
        boolean mAsyncCheckpoint;

        OpenHelper(Context context, String name, final WCDBDatabase[] dbRef,
                   byte[] passphrase, SQLiteCipherSpec cipherSpec,
                   final WCDBCallback callback) {
            super(context, name, passphrase, cipherSpec, null, DbConfig.DB_VERSION, dbObj -> {
                WCDBDatabase db = dbRef[0];
                if (db != null) {
                    callback.onCorruption(db);
                }
            });
            mCallback = callback;
            mDbRef = dbRef;
            mAsyncCheckpoint = false;
        }

        public Database getWritableGDDatabase() {
            return getWrappedDb(super.getWritableDatabase());
        }

        public Database getReadableGDDatabase() {
            return getWrappedDb(super.getReadableDatabase());
        }

        WCDBDatabase getWrappedDb(com.tencent.wcdb.database.SQLiteDatabase sqLiteDatabase) {
            WCDBDatabase dbRef = mDbRef[0];
            if (dbRef == null) {
                dbRef = new WCDBDatabase(sqLiteDatabase);
                mDbRef[0] = dbRef;
            }
            return mDbRef[0];
        }

        @Override
        public void onCreate(com.tencent.wcdb.database.SQLiteDatabase sqLiteDatabase) {
            WCDBDatabase wrappedDb = getWrappedDb(sqLiteDatabase);
            DaoMaster.createAllTables(wrappedDb, true);
            mCallback.onCreate(wrappedDb);
            RepairableDatabase.getDatabase().saveMaster(sqLiteDatabase, new RepairableDatabase.OperateCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "创建时备份成功");
                }

                @Override
                public void onError(String errMsg) {
                    Log.d(TAG, "创建时备份失败，" + errMsg);
                }

                @Override
                public void onCancel() {
                    Log.d(TAG, "创建时备份取消");
                }
            });
        }

        @Override
        public void onUpgrade(com.tencent.wcdb.database.SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            mCallback.onUpgrade(getWrappedDb(sqLiteDatabase), oldVersion, newVersion);
            RepairableDatabase.getDatabase().saveMaster(sqLiteDatabase, new RepairableDatabase.OperateCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "升级时备份成功");
                }

                @Override
                public void onError(String errMsg) {
                    Log.d(TAG, "升级时备份失败，" + errMsg);
                }

                @Override
                public void onCancel() {
                    Log.d(TAG, "升级时备份取消");
                }
            });
        }

        @Override
        public void onConfigure(com.tencent.wcdb.database.SQLiteDatabase db) {
            db.setAsyncCheckpointEnabled(mAsyncCheckpoint);
            mCallback.onConfigure(getWrappedDb(db));
        }

        @Override
        public void onDowngrade(com.tencent.wcdb.database.SQLiteDatabase db, int oldVersion, int newVersion) {
            mCallback.onDowngrade(getWrappedDb(db), oldVersion, newVersion);
        }

        @Override
        public void onOpen(com.tencent.wcdb.database.SQLiteDatabase db) {
            mCallback.onOpen(getWrappedDb(db));
        }

        @Override
        public synchronized void close() {
            super.close();
            mDbRef[0] = null;
        }

        public static void createAllTables(Database db, boolean ifNotExists) {
            WCDBTImgDao.createTable(db, ifNotExists);
            UserDao.createTable(db, ifNotExists);
        }

        public static void dropAllTables(Database db, boolean ifExists) {
            WCDBTImgDao.dropTable(db, ifExists);
            UserDao.dropTable(db, ifExists);
        }
    }

}
