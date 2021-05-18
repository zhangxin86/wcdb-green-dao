package com.example.wcdb;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wcdb.config.Cancelable;
import com.example.wcdb.config.DbHelper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * 数据库恢复测试页面（包括摧毁数据库，恢复数据库等操作）
 * @author zhangxin221
 */
public class TestDbActivity extends AppCompatActivity {
    private final RepairableDatabase appDatabase = RepairableDatabase.getDatabase();
    private TextView tvMsg;
    private Cancelable cancel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_db);
        tvMsg = findViewById(R.id.tv_msg);
        findViewById(R.id.btn_test).setOnClickListener((v) -> {
            try {
                DbHelper.checkIsCorrupted();
                tvMsg.setText("数据库完好");
            } catch (Exception e) {
                tvMsg.setText(String.format("数据库已损坏：%s", e.getMessage()));
            }
        });

        findViewById(R.id.btn_destroy).setOnClickListener((v) -> {
            File database = getDatabasePath(appDatabase.getDbConfig().getDbName());
            if (!database.exists()) {
                tvMsg.setText("摧毁失败，数据库不存在");
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(database, "rw")) {
                byte[] buffer = new byte[1024];
                new Random().nextBytes(buffer);
                raf.seek(0);
                raf.write(buffer);
                DbHelper.checkIsCorrupted();
                tvMsg.setText("数据库未损坏");
            } catch (Exception e) {
                tvMsg.setText(String.format("数据库已损坏：%s", e.getMessage()));
            }
        });

        findViewById(R.id.btn_delete).setOnClickListener(v -> {
            File database = getDatabasePath(appDatabase.getDbConfig().getDbName());
            if (!database.exists()) {
                tvMsg.setText("数据库文件不存在");
                return;
            }
            if (database.delete()) {
                tvMsg.setText("数据库文件已删除");
            } else {
                tvMsg.setText("数据库文件未删除");
            }
        });

        findViewById(R.id.btn_repair).setOnClickListener((v) -> {
            cancel = appDatabase.repairDb(null, false, new RepairableDatabase.OperateCallback() {
                @Override
                public void onSuccess() {
                    tvMsg.setText("修复成功，请重启应用");
                }

                @Override
                public void onError(String errMsg) {
                    tvMsg.setText(String.format("修复失败，失败原因：%s", errMsg));
                }

                @Override
                public void onCancel() {
                    tvMsg.setText("修复被取消");
                }
            });
        });

        findViewById(R.id.btn_backup).setOnClickListener((v) -> {
            cancel = appDatabase.backupDb(null, new RepairableDatabase.OperateCallback() {
                @Override
                public void onSuccess() {
                    tvMsg.setText("备份成功");
                }

                @Override
                public void onError(String errMsg) {
                    tvMsg.setText(String.format("备份失败，失败原因：%s", errMsg));
                }

                @Override
                public void onCancel() {
                    tvMsg.setText("备份被取消");
                }
            });
        });

        findViewById(R.id.btn_recover).setOnClickListener((v) -> {
            cancel = appDatabase.recoverDb(false, new RepairableDatabase.OperateCallback() {
                @Override
                public void onSuccess() {
                    tvMsg.setText("还原成功，请重启应用");
                }

                @Override
                public void onError(String errMsg) {
                    tvMsg.setText(String.format("还原失败，失败原因：%s", errMsg));
                }

                @Override
                public void onCancel() {
                    tvMsg.setText("还原被取消");
                }
            });
        });

        findViewById(R.id.btn_cancel).setOnClickListener((v) -> {
            if (cancel != null) {
                cancel.cancel();
            }
        });
    }
}