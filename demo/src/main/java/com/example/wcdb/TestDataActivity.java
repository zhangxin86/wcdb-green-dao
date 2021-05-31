package com.example.wcdb;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wcdb.config.database.DaoSession;
import com.example.wcdb.entity.User;
import com.example.wcdb.entity.UserDao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据测试页面（增删改查）
 * @author zhangxin221
 */
public class TestDataActivity extends AppCompatActivity {
    private final RepairableDatabase database = RepairableDatabase.getDatabase();
    private final DaoSession daoSession = database.getDaoSession();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView tvMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_data);
        tvMsg = findViewById(R.id.tv_msg);

        findViewById(R.id.btn_add).setOnClickListener((v) -> {
            if (daoSession == null) {
                Toast.makeText(this, "无法获取数据库实例，可能数据库已被破坏", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                UserDao userDao = daoSession.getUserDao();
                User user = new User();
                user.uid = getMaxId() + 1;
                user.userId = String.valueOf(user.uid);
                user.userName = "testUser" + user.userId;
                user.age = 10;
                user.sex = "0";
                userDao.insert(user);
                setMsg("插入操作成功");
            });
        });

        findViewById(R.id.btn_query).setOnClickListener((v) -> {
            if (daoSession == null) {
                Toast.makeText(this, "无法获取数据库实例，可能数据库已被破坏", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                UserDao userDao = daoSession.getUserDao();
                List<User> users = userDao.loadAll();
                String[] userNames = new String[users.size()];
                for (int i = 0; i < users.size(); i++) {
                    userNames[i] = users.get(i).userName;
                }
                showQueryDialog(userNames);
            });
        });

        findViewById(R.id.btn_delete).setOnClickListener((v) -> {
            if (daoSession == null) {
                Toast.makeText(this, "无法获取数据库实例，可能数据库已被破坏", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                database.getDaoSession().getUserDao().deleteAll();
                setMsg("删除操作成功");
            });
        });

        findViewById(R.id.btn_update).setOnClickListener((v) -> {
            if (daoSession == null) {
                Toast.makeText(this, "无法获取数据库实例，可能数据库已被破坏", Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                UserDao userDao = daoSession.getUserDao();
                List<User> users = userDao.loadAll();
                for (User user : users) {
                    user.userName = "update_" + user.userName;
                }
                daoSession.getUserDao().saveInTx(users);
                setMsg("更新操作成功");
            });
        });
    }

    private Long getMaxId() {
        List<User> maxPostIdRow = daoSession.getUserDao().queryBuilder()
                .where(UserDao.Properties.Uid.isNotNull())
                .orderDesc(UserDao.Properties.Uid).limit(1).list();
        if (maxPostIdRow.isEmpty()) {
            return 0L;
        }
        return maxPostIdRow.get(0).getUid();
    }

    private void showQueryDialog(String[] data) {
        runOnUiThread(()-> {
            new AlertDialog.Builder(this)
                    .setTitle("查询结果")
                    .setItems(data, null)
                    .show();
        });
    }

    private void setMsg(String msg) {
        runOnUiThread(() -> tvMsg.setText(msg));
    }
}