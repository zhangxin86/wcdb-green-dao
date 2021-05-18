package com.example.wcdb.config;


import com.example.wcdb.config.exception.CorruptListener;

public class DbConfig {
    public static final int DB_VERSION = 2;
    public static final String DEFAULT_DB_NAME = "app_database.db";
    public static final byte[] DEFAULT_DB_PASSWORD = "default".getBytes();
    public static final byte[] DEFAULT_BACKUP_PASSWORD = "default_backup".getBytes();
    public static final CorruptListener DEFAULT_CORRUPT_LISTENER = (e) -> {
        // do nothing
    };

    private DbConfig() {
//        CorruptExceptionHandler.getInstance().setCorruptListener(DEFAULT_CORRUPT_LISTENER);
    }

    //数据库名称
    private String dbName = DEFAULT_DB_NAME;
    //数据库加密密码
    private byte[] dbPassword = DEFAULT_DB_PASSWORD;
    //数据库备份密码
    private byte[] backupPassword = DEFAULT_BACKUP_PASSWORD;
    //数据库损坏监听
    private CorruptListener listener = DEFAULT_CORRUPT_LISTENER;

    public String getDbName() {
        return dbName;
    }

    public byte[] getDbPassword() {
        return dbPassword;
    }

    public byte[] getBackupPassword() {
        return backupPassword;
    }

    public CorruptListener getCorruptListener() {
        return listener;
    }

    public DbConfig setDbName(String dbName) {
        this.dbName = dbName;
        return this;
    }

    public DbConfig setDbPassword(byte[] dbPassword) {
        this.dbPassword = dbPassword;
        return this;
    }

    public DbConfig setBackupPassword(byte[] backupPassword) {
        this.backupPassword = backupPassword;
        return this;
    }

    public DbConfig setCorruptListener(CorruptListener listener) {
        this.listener = listener;
//        CorruptExceptionHandler.getInstance().setCorruptListener(listener);
        return this;
    }

    public static DbConfig newInstance() {
        return new DbConfig();
    }
}
