package com.example.wcdb.config;

import com.tencent.wcdb.support.CancellationSignal;

/**
 * repair、backup、recover任务取消代理类
 */
public class Cancelable {
    private final CancellationSignal.OnCancelListener proxy;
    public static Cancelable newInstance(CancellationSignal.OnCancelListener proxy) { return new Cancelable(proxy); }
    public static Cancelable emptyCancelable() { return new Cancelable(null); }

    private Cancelable(CancellationSignal.OnCancelListener proxy) {
        this.proxy = proxy;
    }

    public void cancel() {
        if (proxy != null) {
            proxy.onCancel();
        }
    }
}
