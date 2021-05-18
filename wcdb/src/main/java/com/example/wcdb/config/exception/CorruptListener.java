package com.example.wcdb.config.exception;

/**
 * 数据库损坏异常时的回调
 */
public interface CorruptListener {

    void onCorrupt(Throwable e);
}
