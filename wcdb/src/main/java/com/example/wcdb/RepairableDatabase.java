package com.example.wcdb;

import android.app.Application;
import android.util.Log;

import com.example.wcdb.config.Cancelable;
import com.example.wcdb.config.DbConfig;
import com.example.wcdb.config.DbHelper;
import com.example.wcdb.config.database.DaoMaster;
import com.example.wcdb.config.database.DaoSession;
import com.example.wcdb.config.database.WCDBDatabase;
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
import static com.example.wcdb.config.DbHelper.checkIsCorrupted;
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
     * 保存master信息表，与{@link #repairDb}配合使用
     * @param callback 执行回调
     */
    public void saveMaster(SQLiteDatabase sqlite, OperateCallback callback) {
        SINGLE_THREAD_POOL_EXECUTOR.execute(() -> {
            try {
                checkIsCorrupted();
            } catch (Exception e) {
                //数据库master表已损坏
                callback.onError(OperateCallback.ERROR_MAG_DB_SAVE_MASTER);
                return;
            }
            boolean result = RepairKit.MasterInfo.save(sqlite, getMasterInfoSavePath(new File(sqlite.getPath())),
                    mDbConfig.getBackupPassword());
            Log.d(TAG, "数据库备份结果：" + result);
            runOnUiThread(() -> {
                if (result) {
                    successCallback(callback);
                } else {
                    errorCallback(callback, OperateCallback.ERROR_MAG_DB_SAVE_MASTER);
                }
            });
        });
    }

    /**
     * 修复数据库
     * @param tables 修复数据库中的表的表名集合，传null表示全部修复
     * @param isNeedRecover 修复数据库失败后是否需要进行数据库还原，true表示需要，可能会耗费更长的时间，false表示不需要，直接回调修复失败
     * @param callback 执行回调
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
                if (newDbFile.exists()) { newDbFile.delete(); }
                SQLiteDatabase newDb = SQLiteDatabase.openOrCreateDatabase(newDbFile,
                        mDbConfig.getDbPassword(), SQLITE_CIPHER_SPEC, null, ERROR_HANDLER);
                repair[0].setCallback((table, root, cursor) -> {
                    Log.d(TAG, String.format("修复表: %s, root: %d, count: %d",
                            table, root, cursor.getColumnCount()));
                    return RepairKit.RESULT_OK;
                });
                int result = repair[0].output(newDb, 0);
                if (result != RepairKit.RESULT_OK && result != RepairKit.RESULT_CANCELED) {
                    throw new SQLiteException(OperateCallback.ERROR_MAG_DB_REPAIR_OUTPUT_ERROR);
                }
                newDb.setVersion(DB_VERSION);
                newDb.close();
                repair[0].release();
                repair[0] = null;
                if (!dbFile.delete() || !newDbFile.renameTo(dbFile)) {
                    throw new SQLiteException(OperateCallback.ERROR_MAG_DB_REPAIR_RENAME_ERROR);
                }
                //修复成功，重置实例
                initDb(DbHelper.mApplication, mDbConfig);
                //保存master表
                saveMaster((SQLiteDatabase) mDaoMater.getDatabase().getRawDatabase(), callback);
            } catch (Exception e) {
                //修复失败
                if (isNeedRecover) {
                    Log.d(TAG, "repairDb: 修复失败，走恢复流程，" + e.getMessage());
                    //需要恢复，走恢复数据库流程
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
     * 备份数据库，与{@link #recoverDb}配合使用
     * @param tables 需要备份的表的表名，传null表示全部备份
     * @param callback 执行回调
     */
    public Cancelable backupDb(String[] tables, OperateCallback callback) {
        checkIsInit();
        SQLiteDatabase sqlite;
        try {
            sqlite = (SQLiteDatabase) mOpenHelper.getWritableDb().getRawDatabase();
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
     * 恢复数据库，与{@link #backupDb}配合使用
     * @param fatal true表示恢复过程中遇到错误，结束恢复过程，并回调callback的onCancel方法，false表示忽略错误，继续执行
     * @param callback 执行回调
     */
    public Cancelable recoverDb(boolean fatal, OperateCallback callback) {
        checkIsInit();
        File database = getDbPath(mDbConfig.getDbName());
        File backupFile = new File(getBackupPath(database));
        if (!backupFile.exists()) {
            errorCallback(callback, OperateCallback.ERROR_MAG_DB_RECOVER_NOT_BACKUP);
            return Cancelable.emptyCancelable();
        }
        SQLiteDatabase sqlite;
        try {
            sqlite = (SQLiteDatabase) mOpenHelper.getWritableDb().getRawDatabase();
        } catch (SQLiteDatabaseCorruptException | IllegalStateException e) {
            //当前文件报异常，删除重建
            if (database.exists() && !database.delete()) {
                return Cancelable.emptyCancelable();
            }
            sqlite = SQLiteDatabase.openOrCreateDatabase(database,
                    mDbConfig.getDbPassword(), SQLITE_CIPHER_SPEC, null, ERROR_HANDLER);
        }
        try {
            RecoverKit recover = new RecoverKit(sqlite, backupFile.getPath(), mDbConfig.getBackupPassword());
            SINGLE_THREAD_POOL_EXECUTOR.execute(() -> {
                int result = recover.run(fatal);
                runOnUiThread(() -> {
                    switch (result) {
                        case RecoverKit.RESULT_OK:
                            //重置数据库实例
                            initDb(DbHelper.mApplication, mDbConfig);
                            saveMaster(getWcdbSqlite(), callback);
                            break;
                        case RecoverKit.RESULT_CANCELED:
                            cancelCallback(callback);
                            break;
                        case RecoverKit.RESULT_FAILED:
                            errorCallback(callback, OperateCallback.ERROR_MAG_DB_RECOVER_FAILED);
                            break;
                    }
                });
                recover.release();
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
     * 操作回调接口
     */
    public interface OperateCallback {
        String ERROR_MAG_DB_UNINITIALIZED = "数据库获取失败，未经过初始化";
        String ERROR_MAG_DB_SAVE_MASTER = "数据库master信息保存失败";
        String ERROR_MAG_DB_SAVE_MASTER_ERROR_FILE = "数据库master信息保存失败，当前数据库文件已损坏";
        String ERROR_MAG_DB_REPAIR_FILE_ERROR = "数据库修复操作失败，可能备份文件被破坏";
        String ERROR_MAG_DB_REPAIR_NOT_EXIST_MASTER_INFO = "数据库修复操作失败，不存在备份文件";
        String ERROR_MAG_DB_REPAIR_OUTPUT_ERROR = "数据库修复操作失败，无法将数据输出到新数据库";
        String ERROR_MAG_DB_REPAIR_RENAME_ERROR = "数据库修复操作失败，无法重命名";
        String ERROR_MAG_DB_BACKUP_FAILED = "数据库备份操作失败";
        String ERROR_MAG_DB_BACKUP_FAILED_ERROR_FILE = "数据库备份操作失败，当前数据库文件已损坏";
        String ERROR_MAG_DB_RECOVER_FAILED = "数据库恢复操作失败";
        String ERROR_MAG_DB_RECOVER_NOT_BACKUP = "数据库恢复操作失败，当前数据库没有备份";
        void onSuccess();
        void onError(String errMsg);
        void onCancel();
    }

}
