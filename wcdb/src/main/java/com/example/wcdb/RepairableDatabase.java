package com.example.wcdb;

import android.app.Application;
import android.util.Log;

import com.example.wcdb.config.Cancelable;
import com.example.wcdb.config.DbConfig;
import com.example.wcdb.config.DbHelper;
import com.example.wcdb.config.database.DaoMaster;
import com.example.wcdb.config.database.DaoSession;
import com.example.wcdb.config.database.WCDBOpenHelper;
import com.example.wcdb.config.exception.CorruptListener;
import com.tencent.wcdb.database.SQLiteDatabase;
import com.tencent.wcdb.database.SQLiteDatabaseCorruptException;
import com.tencent.wcdb.database.SQLiteException;
import com.tencent.wcdb.repair.BackupKit;
import com.tencent.wcdb.repair.RecoverKit;
import com.tencent.wcdb.repair.RepairKit;

import org.greenrobot.greendao.database.Database;

import java.io.File;

import static com.example.wcdb.config.DbConfig.DB_VERSION;
import static com.example.wcdb.config.DbHelper.ERROR_HANDLER;
import static com.example.wcdb.config.DbHelper.GREEN_DAO_CALLBACK;
import static com.example.wcdb.config.DbHelper.SINGLE_THREAD_POOL_EXECUTOR;
import static com.example.wcdb.config.DbHelper.SQLITE_CIPHER_SPEC;
import static com.example.wcdb.config.DbHelper.checkIsInit;
import static com.example.wcdb.config.DbHelper.getBackupPath;
import static com.example.wcdb.config.DbHelper.getDbPath;
import static com.example.wcdb.config.DbHelper.getMasterInfoSavePath;
import static com.example.wcdb.config.DbHelper.mDbConfig;
import static com.example.wcdb.config.DbHelper.runOnUiThread;

public class RepairableDatabase {
    public static final String TAG = RepairableDatabase.class.getName();
    private WCDBOpenHelper mOpenHelper;
    private DaoSession mDaoSession;
    private DaoMaster mDaoMater;
    private static RepairableDatabase INSTANCE = new RepairableDatabase();
    public static RepairableDatabase getDatabase() { return INSTANCE; }
    private RepairableDatabase(){ }

    public void init(Application application, DbConfig dbConfig) {
        DbHelper.init(application, dbConfig);
        initDb(application, dbConfig);
    }
    private void initDb(Application application, DbConfig dbConfig){
        if (mDaoMater != null) {
            mDaoMater.getDatabase().close();
        }
        mOpenHelper = new WCDBOpenHelper(application, dbConfig.getDbName(), dbConfig.getDbPassword(),
                SQLITE_CIPHER_SPEC, GREEN_DAO_CALLBACK);
        try {
            mDaoMater = new DaoMaster(mOpenHelper.getWritableDb());
            mDaoSession = mDaoMater.newSession();
        } catch (SQLiteDatabaseCorruptException e) {
            CorruptListener corruptListener = dbConfig.getCorruptListener();
            if (corruptListener != null) {
                corruptListener.onCorrupt(e);
            }
        }
    }

    public DaoSession getDaoSession() {
        return mDaoSession;
    }

    public DbConfig getDbConfig() {
        return mDbConfig;
    }

    /**
     * ??????master???????????????{@link #repairDb}????????????
     * @param callback ????????????
     */
    public void saveMaster(OperateCallback callback) {
        SINGLE_THREAD_POOL_EXECUTOR.execute(() -> {
            SQLiteDatabase sqlite;
            try {
                sqlite = getWcdbSqlite();
            } catch (Exception e) {
                //?????????master????????????
                errorCallback(callback, OperateCallback.ERROR_MAG_DB_SAVE_MASTER);
                return;
            }
            boolean result = RepairKit.MasterInfo.save(sqlite, getMasterInfoSavePath(new File(sqlite.getPath())),
                    mDbConfig.getBackupPassword());
            Log.d(TAG, "????????????????????????" + result);
            if (result) {
                successCallback(callback);
            } else {
                errorCallback(callback, OperateCallback.ERROR_MAG_DB_SAVE_MASTER);
            }
        });
    }

    /**
     * ???????????????
     * @param tables ?????????????????????????????????????????????null??????????????????
     * @param isNeedRecover ????????????????????????????????????????????????????????????true????????????????????????????????????????????????false??????????????????????????????????????????
     * @param callback ????????????
     */
    public Cancelable repairDb(String[] tables, boolean isNeedRecover, OperateCallback callback) {
        checkIsInit();
        final File dbFile = getDbPath(mDbConfig.getDbName());
        final File masterFile = new File(dbFile.getPath() + "-mbak");
        final File newDbFile = getDbPath(mDbConfig.getDbName() + "-recover");
        if (!masterFile.exists()) {
            errorCallback(callback, OperateCallback.ERROR_MAG_DB_REPAIR_NOT_EXIST_MASTER_INFO);
            return Cancelable.emptyCancelable();
        }
        final RepairKit[] repair = {null};
        SINGLE_THREAD_POOL_EXECUTOR.execute(()-> {
            RepairKit.MasterInfo master;
            try {
                master = RepairKit.MasterInfo.load(masterFile.getPath(),
                        mDbConfig.getBackupPassword(), tables);
            } catch (SQLiteException e) {
                if (isNeedRecover) {
                    recoverDb(false, callback);
                } else {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_REPAIR_FILE_ERROR);
                }
                return;
            }
            if (mDaoMater != null) {
                Database database = mDaoMater.getDatabase();
                if (database != null && database.isOpen()) {
                    database.close();
                }
            }
            try {
                repair[0] = new RepairKit(dbFile.getPath(), mDbConfig.getDbPassword(),
                        SQLITE_CIPHER_SPEC, master);
                if (newDbFile.exists() && !newDbFile.delete()) {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_DELETE_TEMP_ERROR);
                    return;
                }
                SQLiteDatabase newDb = SQLiteDatabase.openOrCreateDatabase(newDbFile,
                        mDbConfig.getDbPassword(), SQLITE_CIPHER_SPEC, null, ERROR_HANDLER);
                repair[0].setCallback((table, root, cursor) -> {
                    Log.d(TAG, String.format("?????????: %s, root: %d, count: %d",
                            table, root, cursor.getColumnCount()));
                    return RepairKit.RESULT_OK;
                });
                int result = repair[0].output(newDb, 0);
                if (result != RepairKit.RESULT_OK && result != RepairKit.RESULT_CANCELED) {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_REPAIR_OUTPUT_ERROR);
                    return;
                }
                newDb.setVersion(DB_VERSION);
                newDb.close();
                repair[0].release();
                repair[0] = null;
                if ((dbFile.exists() && !dbFile.delete()) || !newDbFile.renameTo(dbFile)) {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_RENAME_ERROR);
                    return;
                }
                //???????????????????????????
                initDb(DbHelper.mApplication, mDbConfig);
                //??????master???
                saveMaster(callback);
            } catch (Exception e) {
                //????????????
                if (isNeedRecover) {
                    Log.d(TAG, "repairDb: ?????????????????????????????????" + e.getMessage());
                    //???????????????????????????????????????
                    recoverDb(false, callback);
                    return;
                }
                errorCallback(callback, e.getMessage());
            } finally {
                if (repair[0] != null) {
                    repair[0].release();
                }
            }
        });
        return Cancelable.newInstance(repair[0]);
    }


    /**
     * ?????????????????????{@link #recoverDb}????????????
     * @param tables ?????????????????????????????????null??????????????????
     * @param callback ????????????
     */
    public Cancelable backupDb(String[] tables, OperateCallback callback) {
        checkIsInit();
        SQLiteDatabase sqlite;
        try {
            sqlite = getWcdbSqlite();
            if (sqlite == null) {
                errorCallback(callback, OperateCallback.ERROR_MAG_DB_UNINITIALIZED);
                return Cancelable.emptyCancelable();
            }
        } catch (SQLiteDatabaseCorruptException | IllegalStateException e) {
            errorCallback(callback, OperateCallback.ERROR_MAG_DB_BACKUP_FAILED_ERROR_FILE);
            return Cancelable.emptyCancelable();
        }
        try {
            BackupKit backupKit = new BackupKit(sqlite, getBackupPath(new File(sqlite.getPath())),
                    mDbConfig.getBackupPassword(), 0, tables);
            SINGLE_THREAD_POOL_EXECUTOR.execute(()-> {
                int result = backupKit.run();
                runOnUiThread(() -> {
                    switch (result) {
                        case BackupKit.RESULT_OK:
                            successCallback(callback);
                            break;
                        case BackupKit.RESULT_CANCELED:
                            cancelCallback(callback);
                            break;
                        case BackupKit.RESULT_FAILED:
                            errorCallback(callback, OperateCallback.ERROR_MAG_DB_BACKUP_FAILED);
                            break;
                    }
                });
                backupKit.release();
            });
            return Cancelable.newInstance(backupKit);
        } catch (SQLiteException e) {
            errorCallback(callback, e.getMessage());
            return Cancelable.emptyCancelable();
        }
    }

    /**
     * ?????????????????????{@link #backupDb}????????????
     * @param fatal true??????????????????????????????????????????????????????????????????callback???onCancel?????????false?????????????????????????????????
     * @param callback ????????????
     */
    public Cancelable recoverDb(boolean fatal, OperateCallback callback) {
        File dbFile = getDbPath(mDbConfig.getDbName());
        File backupFile = new File(getBackupPath(dbFile));
        final File newDbFile = getDbPath(mDbConfig.getDbName() + "-recover2");
        if (!backupFile.exists()) {
            errorCallback(callback, OperateCallback.ERROR_MAG_DB_RECOVER_NOT_BACKUP);
            return Cancelable.emptyCancelable();
        }
        if (newDbFile.exists() && !newDbFile.delete()) {
            errorCallback(callback, OperateCallback.ERROR_MAG_DB_DELETE_TEMP_ERROR);
        }
        SQLiteDatabase newDb = SQLiteDatabase.openOrCreateDatabase(newDbFile,
                mDbConfig.getDbPassword(), SQLITE_CIPHER_SPEC, null, ERROR_HANDLER);
        try {
            RecoverKit recover = new RecoverKit(newDb, backupFile.getPath(), mDbConfig.getBackupPassword());
            SINGLE_THREAD_POOL_EXECUTOR.execute(() -> {
                int result = recover.run(fatal);
                if (result != RepairKit.RESULT_OK && result != RepairKit.RESULT_CANCELED) {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_RECOVER_FAILED);
                    return;
                }
                newDb.setVersion(DB_VERSION);
                newDb.close();
                recover.release();
                if ((dbFile.exists() && !dbFile.delete()) || !newDbFile.renameTo(dbFile)) {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_RENAME_ERROR);
                    return;
                }
                //?????????????????????
                initDb(DbHelper.mApplication, mDbConfig);
                //??????master???
                saveMaster(callback);
            });
            return Cancelable.newInstance(recover);
        } catch (SQLiteException e) {
            errorCallback(callback, e.getMessage());
            return Cancelable.emptyCancelable();
        }
    }

    public SQLiteDatabase getWcdbSqlite() {
        return (SQLiteDatabase) mOpenHelper.getWritableDb().getRawDatabase();
    }

    public void successCallback(OperateCallback callback) {
        if (callback != null) {
            runOnUiThread(callback::onSuccess);
        }
    }

    public void errorCallback(OperateCallback callback, String errorMsg) {
        if (callback != null) {
            runOnUiThread(() -> {
                callback.onError(errorMsg);
            });
        }
    }

    public void cancelCallback(OperateCallback callback) {
        if (callback != null) {
            runOnUiThread(callback::onCancel);
        }
    }

    /**
     * ??????????????????
     */
    public interface OperateCallback {
        String ERROR_MAG_DB_UNINITIALIZED = "??????????????????????????????????????????";
        String ERROR_MAG_DB_SAVE_MASTER = "?????????master??????????????????";
        String ERROR_MAG_DB_SAVE_MASTER_ERROR_FILE = "?????????master???????????????????????????????????????????????????";
        String ERROR_MAG_DB_REPAIR_FILE_ERROR = "?????????????????????????????????????????????????????????";
        String ERROR_MAG_DB_REPAIR_NOT_EXIST_MASTER_INFO = "???????????????????????????????????????????????????";
        String ERROR_MAG_DB_REPAIR_OUTPUT_ERROR = "??????????????????????????????????????????????????????????????????";
        String ERROR_MAG_DB_RENAME_ERROR = "???????????????????????????????????????";
        String ERROR_MAG_DB_DELETE_TEMP_ERROR = "????????????????????????????????????????????????";
        String ERROR_MAG_DB_BACKUP_FAILED = "???????????????????????????";
        String ERROR_MAG_DB_BACKUP_FAILED_ERROR_FILE = "????????????????????????????????????????????????????????????";
        String ERROR_MAG_DB_RECOVER_FAILED = "???????????????????????????";
        String ERROR_MAG_DB_RECOVER_NOT_BACKUP = "?????????????????????????????????????????????????????????";
        void onSuccess();
        void onError(String errMsg);
        void onCancel();
    }

}
