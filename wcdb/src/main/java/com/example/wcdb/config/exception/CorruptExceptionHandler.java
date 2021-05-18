package com.example.wcdb.config.exception;

import android.util.Log;

import com.example.wcdb.config.DbHelper;
import com.tencent.wcdb.database.SQLiteDatabaseCorruptException;

public class CorruptExceptionHandler implements Thread.UncaughtExceptionHandler{
    private static final String TAG = CorruptExceptionHandler.class.getName();
    private final Thread.UncaughtExceptionHandler sysDefaultHandler;
    private CorruptListener mListener = null;
    private static final CorruptExceptionHandler INSTANCE = new CorruptExceptionHandler();
    public static CorruptExceptionHandler getInstance() { return INSTANCE; }
    private CorruptExceptionHandler() {
        sysDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public void setCorruptListener(CorruptListener listener) {
        mListener = listener;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (e instanceof SQLiteDatabaseCorruptException ||
                (e instanceof  IllegalStateException && e.getMessage() != null &&
                        e.getMessage().contains("attempt to re-open an already-closed object"))) {
            Log.e(TAG, "数据库错误发生：" + e.getMessage());
            if (mListener != null) {
                DbHelper.runOnUiThread(()-> mListener.onCorrupt(e));
            }
        } else {
            if (sysDefaultHandler != null) {
                sysDefaultHandler.uncaughtException(t, e);
            } else if (!(e instanceof ThreadDeath)) {
                System.err.print("Exception in thread \""
                        + t.getName() + "\" ");
                e.printStackTrace(System.err);
            } else {
                System.exit(0);
            }
        }
    }
}
