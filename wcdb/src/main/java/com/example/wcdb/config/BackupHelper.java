package com.example.wcdb.config;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.wcdb.RepairableDatabase;

/**
 * 数据库备份服务帮助类
 * @author zhangxin221
 */
public class BackupHelper {
    private static final String TAG = BackupHelper.class.getName();

    public static void startBackupService(Application application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //根据微信公众号建议，在设备充电灭屏状态下使用JobScheduler进行数据库备份
            //link：https://mp.weixin.qq.com/s/Ln7kNOn3zx589ACmn5ESQA
            JobInfo.Builder builder = new JobInfo.Builder(1, new ComponentName(application,
                    DbBackupJobService.class))
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(false);
            JobScheduler scheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            scheduler.schedule(builder.build());
        } else {
            //官方JobScheduler只支持到5.0版本系统，这里对于低版本的系统使用第三方框架进行适配
            //link：https://github.com/evant/JobSchedulerCompat
            me.tatarka.support.job.JobInfo.Builder builder =
                    new me.tatarka.support.job.JobInfo.Builder(1, new ComponentName(application,
                            DbBackupJobServiceL.class))
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true);
            me.tatarka.support.job.JobScheduler scheduler = me.tatarka.support.job.JobScheduler.getInstance(application);
            scheduler.schedule(builder.build());
        }
    }

    public static void stopBackupService(Application application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler scheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            scheduler.cancelAll();
        } else {
            me.tatarka.support.job.JobScheduler scheduler = me.tatarka.support.job.JobScheduler.getInstance(application);
            scheduler.cancelAll();
        }
    }

    /**
     * 定期备份数据库的后台任务（适用于android 5.0及以上版本）
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class DbBackupJobService extends JobService {
        private Cancelable mCancelHandel;

        @Override
        public boolean onStartJob(JobParameters params) {
            if (mCancelHandel == null) {
                Log.i(TAG, "onStartJob: 备份任务开始执行");
                mCancelHandel = RepairableDatabase.getDatabase().backupDb(null, new RepairableDatabase.OperateCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "onSuccess: 备份任务执行完成");
                        jobFinished(params, true);
                        mCancelHandel = null;
                    }

                    @Override
                    public void onError(String errMsg) {
                        Log.i(TAG, "onError: 备份任务执行完成，" + errMsg);
                        jobFinished(params, true);
                        mCancelHandel = null;
                    }

                    @Override
                    public void onCancel() {
                        Log.i(TAG, "onCancel: 备份任务执行完成");
                        jobFinished(params, false);
                        mCancelHandel = null;
                    }
                });
            }
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            Log.i(TAG, "onStartJob: 备份任务结束");
            return false;
        }
    }

    /**
     * 定期备份数据库的后台任务（适用于android 5.0以下版本）
     */
    public static class DbBackupJobServiceL extends me.tatarka.support.job.JobService {
        private Cancelable mCancelHandel;
        @Override
        public boolean onStartJob(me.tatarka.support.job.JobParameters params) {
            if (mCancelHandel == null) {
                Log.i(TAG, "onStartJob: 备份任务开始执行");
                mCancelHandel = RepairableDatabase.getDatabase().backupDb(null, new RepairableDatabase.OperateCallback() {
                    @Override
                    public void onSuccess() {
                        jobFinished(params, true);
                        mCancelHandel = null;
                    }

                    @Override
                    public void onError(String errMsg) {
                        jobFinished(params, true);
                        mCancelHandel = null;
                    }

                    @Override
                    public void onCancel() {
                        jobFinished(params, false);
                        mCancelHandel = null;
                    }
                });
            }
            return true;
        }

        @Override
        public boolean onStopJob(me.tatarka.support.job.JobParameters params) {
            Log.i(TAG, "onStartJob: 备份任务结束");
            return false;
        }
    }
}
