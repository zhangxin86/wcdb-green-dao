package com.example.wcdb;

import android.app.Application;
import android.widget.Toast;

import com.example.wcdb.config.DbConfig;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RepairableDatabase.getDatabase().init(this, DbConfig.newInstance()
        .setCorruptListener((e) -> {
            //数据库发生错误，需要提示用户进行修复
            Toast.makeText(this, "数据库异常，请进行修复，" + e.getMessage(), Toast.LENGTH_LONG).show();
        }));
    }


}
