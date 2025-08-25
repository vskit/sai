package com.apks.sai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

public class InstallReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install result - Status: " + status + ", SessionID: " +
                sessionId + ", Package: " + packageName);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.i(TAG, "STATUS_PENDING_USER_ACTION");
                Intent userAction = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (userAction != null) {
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(userAction);
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "STATUS_SUCCESS: " + packageName);
                Toast.makeText(context, "STATUS_SUCCESS", Toast.LENGTH_SHORT).show();
                break;

            case PackageInstaller.STATUS_FAILURE_ABORTED:
                Log.e(TAG, "STATUS_FAILURE_ABORTED: " + message);
                break;

            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                Log.e(TAG, "STATUS_FAILURE_BLOCKED: " + message);
                Toast.makeText(context, "STATUS_FAILURE_BLOCKED: " + message, Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                Log.e(TAG, "STATUS_FAILURE_CONFLICT: " + message);
                break;

            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                Log.e(TAG, "STATUS_FAILURE_INCOMPATIBLE: " + message);
                break;

            case PackageInstaller.STATUS_FAILURE_INVALID:
                Log.e(TAG, "STATUS_FAILURE_INVALID: " + message);
                break;

            case PackageInstaller.STATUS_FAILURE_STORAGE:
                Log.e(TAG, "STATUS_FAILURE_STORAGE: " + message);
                break;

            default:
                Log.e(TAG, "unknown: " + message);
                break;
        }

        App.getInstance().notifyInstallResult(status == PackageInstaller.STATUS_SUCCESS, message);
    }
}