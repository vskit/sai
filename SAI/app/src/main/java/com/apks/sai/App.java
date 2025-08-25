package com.apks.sai;

import android.app.Application;
import java.util.ArrayList;
import java.util.List;

public class App extends Application {
    private static App instance;
    private final List<InstallResultListener> listeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // 确保在onCreate中初始化实例
    }

    public static App getInstance() {
        return instance;
    }

    // 使用ApplicationContext而不是静态实例
    public void addInstallResultListener(InstallResultListener listener) {
        listeners.add(listener);
    }

    public void removeInstallResultListener(InstallResultListener listener) {
        listeners.remove(listener);
    }

    public void notifyInstallResult(boolean success, String message) {
        for (InstallResultListener listener : new ArrayList<>(listeners)) {
            listener.onInstallResult(success, message);
        }
    }

    public interface InstallResultListener {
        void onInstallResult(boolean success, String message);
    }
}
