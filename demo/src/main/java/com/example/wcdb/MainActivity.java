package com.example.wcdb;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_db).setOnClickListener((v) -> startActivity(new Intent(this, TestDbActivity.class)));
        findViewById(R.id.btn_data).setOnClickListener((v) -> startActivity(new Intent(this, TestDataActivity.class)));

    }
}