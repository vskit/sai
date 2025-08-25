package com.apks.sai;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class InstallService extends Service {

    private static final String TAG = "InstallService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "install_channel";

//    // 需要安装的APK文件类型
//    private static final String[] REQUIRED_APKS = {
//            "/splits/base-master_2.apk",
//            "/splits/base-arm64_v8a.apk",
//            "/splits/base-xxhdpi.apk",
//            "/splits/base-zh.apk",
//            "/asset-slices/obbassets-master.apk"
//    };

    private static Deque<String> apkFilesDeque = new ArrayDeque<>();
    private static boolean bFoundBaseApk = false;

    public static void setSelectApkFiles(List<String> selectedApks) {
        apkFilesDeque.clear();
        bFoundBaseApk = false;
        for (String apkFilePath : selectedApks) {
            String fileName = getSimpleFileName(apkFilePath);
            if (fileName.contains("base-master")) {
                bFoundBaseApk = true;
                apkFilesDeque.addFirst(apkFilePath);
            } else {
                apkFilesDeque.addLast(apkFilePath);
            }
        }
    }

    private static String getSimpleFileName(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < fullPath.length() - 1) {
            return fullPath.substring(lastSlash + 1);
        }
        return fullPath.toLowerCase();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            installApks();
        }
        return START_NOT_STICKY;
    }


    private void installApks() {
        if (!bFoundBaseApk)
        {
            showFailureNotification("installApks install fail no base apk");
            return;
        }
        try {
            // 获取PackageInstaller
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();

            // 创建会话参数
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO);

            // 创建会话
            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            // 写入所有APK文件
            for (String apkFilePath : apkFilesDeque) {
                File apkFile = new File(apkFilePath);
                writeApkToSession(session, apkFile);
            }

            // 创建广播接收PendingIntent
            Intent broadcastIntent = new Intent(this, InstallReceiver.class);
            broadcastIntent.putExtra("session_id", sessionId);

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingFlags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, sessionId, broadcastIntent, pendingFlags);

            // 提交安装会话
            session.commit(pendingIntent.getIntentSender());
            session.close();

            updateNotification("installing...");
            Log.i(TAG, "install session commit, ID: " + sessionId);

        } catch (Exception e) {
            Log.e(TAG, "install fail", e);
            showFailureNotification("install fail: " + e.getMessage());
            stopSelf();
        }
    }

    private void writeApkToSession(PackageInstaller.Session session, File apkFile) throws Exception {
        String splitName = apkFile.getName().replace(".apk", "");

        try (InputStream in = new FileInputStream(apkFile);
             OutputStream out = session.openWrite(splitName, 0, apkFile.length())) {

            byte[] buffer = new byte[1024 * 1024];
            int length;
            while ((length = in.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
            session.fsync(out);
        }

        Log.d(TAG, "writeApkToSession: " + apkFile.getName());
    }

    private Notification createNotification(String text) {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("APK INSTALLER")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }

    private void updateNotification(String text) {
        Notification notification = createNotification(text);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Install Notification",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("APK安装状态通知");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showFailureNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Install Fail")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID + 1, builder.build());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 移除通知
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}