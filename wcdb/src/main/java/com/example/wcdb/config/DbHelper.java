package com.example.wcdb.config;

import android.app.Application;
import android.os.Handler;

import com.example.wcdb.config.database.WCDBOpenHelper;
import com.example.wcdb.config.exception.CorruptExceptionHandler;
import com.tencent.wcdb.DatabaseErrorHandler;
import com.tencent.wcdb.database.SQLiteCipherSpec;
import com.tencent.wcdb.database.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DbHelper {
    public static final String TAG = DbHelper.class.getName();
    public static final WCDBCallback GREEN_DAO_CALLBACK = new WCDBCallback() {
        @Override
        public void onCreate(Database db) {

        }
        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) {
                db.execSQL("ALTER TABLE t_user ADD COLUMN sex TEXT NOT NULL DEFAULT '0'");
            }
        }
    };
    public static final DatabaseErrorHandler ERROR_HANDLER = dbObj -> {
        // Do nothing
        // 不设置的话，数据库在打开时报错会自动删除报错数据库文件，导致无法进行后续修复
    };
    public static final SQLiteCipherSpec SQLITE_CIPHER_SPEC = new SQLiteCipherSpec()
            .setPageSize(1024);
    public static final int LIMIT_QUEUE_SIZE = 1000;
    public static final ThreadPoolExecutor SINGLE_THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(LIMIT_QUEUE_SIZE), new ThreadPoolExecutor.CallerRunsPolicy());
    public static Handler mUiHandler;
    public static Application mApplication;
    public static DbConfig mDbConfig;
    private static boolean initFlag = false;


    public static void init(Application application, DbConfig dbConfig) {
        initFlag = true;
        mApplication = application;
        mDbConfig = dbConfig;
        mUiHandler = new Handler(application.getMainLooper());
        //启动定期备份后台服务
        BackupHelper.startBackupService(application);
        CorruptExceptionHandler.getInstance().setCorruptListener(dbConfig.getCorruptListener());
    }

    public static void checkIsInit() {
        if (!initFlag) {
            throw new IllegalStateException("请先调用init方法进行初始化！！");
        }
    }

    public static void runOnUiThread(Runnable runnable) {
        mUiHandler.post(runnable);
    }

    /**
     * 获取系统数据库路径
     * @param dbName 数据库名称
     * @return 数据库路径
     */
    public static File getDbPath(String dbName) {
        return mApplication.getDatabasePath(dbName);
    }

    /**
     * 获取MasterInfo的备份路径
     * @param database 数据库实例，用于获取系统数据库路径
     * @return MasterInfo的备份路径
     */
    public static String getMasterInfoSavePath(File database) {
        return database.getPath() + "-mbak";
    }

    /**
     * 获取数据库备份路径
     * @param database 数据库实例，用于获取系统数据库路径
     * @return 数据库的备份路径
     */
    public static String getBackupPath(File database) {
        return database.getPath() + "-backup";
    }

    public static void checkIsCorrupted() {
        Database db = new WCDBOpenHelper(mApplication, mDbConfig.getDbName(), mDbConfig.getDbPassword(),
                SQLITE_CIPHER_SPEC, GREEN_DAO_CALLBACK).getWritableDb();
        db.close();
    }
}
