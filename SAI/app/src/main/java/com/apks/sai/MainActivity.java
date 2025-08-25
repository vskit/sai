package com.apks.sai;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity implements App.InstallResultListener {

    private static final int REQUEST_CODE_PICK_APKS = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;
    private static final int REQUEST_INSTALL_PERMISSION = 1003;
    private static final String TAG = "APKSInstaller";
    private static final String FILE_PROVIDER_AUTHORITY = "com.example.apksinstaller.fileprovider";

    private RecyclerView recyclerView;
    private Button btnSelect;
    private Button btnInstall;
    private ProgressBar progressBar;
    private ApkAdapter adapter;
    private final List<ApkItem> apkItems = new ArrayList<>();
    private File tempApksFile;
    private File tempApksDir;

    private String deviceAbi ; // e.g., "arm64-v8a"
    private String deviceLanguage;
    private String deviceDensity; // e.g., "xxxhdpi"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        App.getInstance().addInstallResultListener(this);

        setContentView(R.layout.activity_main);

        initDeviceInfo();

        initView();

        if (!checkInstallPermission()) return;
    }

    private void initView()
    {
        recyclerView = findViewById(R.id.recyclerView);
        btnSelect = findViewById(R.id.btnSelect);
        btnInstall = findViewById(R.id.btnInstall);
        progressBar = findViewById(R.id.progressBar);

        adapter = new ApkAdapter(apkItems, new ApkAdapter.OnItemCheckedChangeListener() {
            @Override
            public void onItemCheckedChanged(int position, boolean isChecked) {
                // 这里可以添加额外的处理逻辑
                Log.d(TAG, "apkItems position " + position + " change to " + isChecked);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSelect.setOnClickListener(v -> {
            openFilePicker();
        });

        btnInstall.setOnClickListener(v -> startInstallService());
        btnInstall.setEnabled(false);
    }

    private void initDeviceInfo()
    {
        deviceAbi = Build.SUPPORTED_ABIS[0];
        deviceLanguage = Locale.getDefault().getLanguage();
        deviceDensity = getDensityGroup();
        deviceAbi = deviceAbi.replace("-", "_");

        Log.i(TAG, "deviceAbi:" + deviceAbi);
        Log.i(TAG, "deviceLanguage:" + deviceLanguage);
        Log.i(TAG, "deviceDensity:" + deviceDensity);
    }

    private String getDensityGroup() {
        int density = getResources().getDisplayMetrics().densityDpi;
        if (density >= 640) return "xxxhdpi";
        else if (density >= 480) return "xxhdpi";
        else if (density >= 320) return "xhdpi";
        else if (density >= 240) return "hdpi";
        else return "mdpi";
    }

    private boolean checkInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                // 正确方法：启动系统设置界面请求权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
                } catch (ActivityNotFoundException e) {
                    // 处理异常：有些设备可能有不同的设置路径
                    Intent fallbackIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    startActivityForResult(fallbackIntent, REQUEST_INSTALL_PERMISSION);
                }
                Log.e(TAG, "no permission ACTION_MANAGE_UNKNOWN_APP_SOURCES" );
                return false;
            }
        }
        return true;
    }


    private boolean checkStoragePermission() {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//            if (android.os.Environment.isExternalStorageManager()) {
//                return true;
//            }
//            // 请求 MANAGE_EXTERNAL_STORAGE 权限
//            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
//            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
//            startActivityForResult(intent, REQUEST_STORAGE_PERMISSION);
//            Log.e(TAG, "no permission ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" );
//            return false;
//        }
        return true;
    }


    private void openFilePicker() {
        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("*/*");
        getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        getContentIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(getContentIntent, REQUEST_CODE_PICK_APKS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_APKS && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                Log.i(TAG, "onActivityResult getClipData" );
                ClipData clipData = data.getClipData();
                if (clipData.getItemCount() > 0) {
                    Log.i(TAG, "onActivityResult getClipData > 0" );
                    Uri firstFileUri = clipData.getItemAt(0).getUri();
                    handleSelectedFile(firstFileUri);
                }
            } else if (data.getData() != null) {
                Log.i(TAG, "onActivityResult getData" );
                handleSelectedFile(data.getData());
            } else {
                Log.e(TAG, "onActivityResult not getData" );
                Toast.makeText(this, "onActivityResult cannot find URI", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == REQUEST_INSTALL_PERMISSION) {
            // 重新检查权限状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    getPackageManager().canRequestPackageInstalls()) {
                // 用户已授权，执行安装
                // yourInstallLogic();
            } else {
                // 权限未授予，显示提示
                Toast.makeText(this, "安装权限被拒绝，无法更新应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleSelectedFile(Uri fileUri) {
        progressBar.setVisibility(View.VISIBLE);
        apkItems.clear();
        adapter.notifyDataSetChanged();
        btnInstall.setEnabled(false);

        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(fileUri)) {
                if (inputStream == null) {
                    throw new IOException("handleSelectedFile can not open inputStream");
                }

                // 创建临时目录
                tempApksDir = new File(getExternalCacheDir(), "extracted_apks");
                if (!tempApksDir.exists()) {
                    if (!tempApksDir.mkdirs()) {
                        throw new IOException("handleSelectedFile can not create tempApksDir");
                    }
                } else {
                    // 清空临时目录
                    deleteRecursive(tempApksDir);
                    tempApksDir.mkdirs();
                }

                // 解压 APKS 文件到临时目录
                try (ZipInputStream zipStream = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    while ((entry = zipStream.getNextEntry()) != null) {
                        if (entry.isDirectory()) continue;

                        String name = entry.getName();
                        String fileName = getSimpleFileName(name);
                        if (!fileName.endsWith(".apk")) continue;

                        // 创建目标文件
                        File outputFile = new File(tempApksDir, fileName);

                        try (FileOutputStream out = new FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = zipStream.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }

                        if (fileName.contains("-" + deviceAbi))
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), true));
                        }
                        else if (fileName.contains("-" + deviceLanguage))
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), true));
                        }
                        else if (fileName.contains("-" + deviceDensity))
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), true));
                        }
                        else if (fileName.contains("obbassets"))
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), true));
                        }
                        else if (fileName.contains("base-master.apk"))
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), true));
                        }
                        else
                        {
                            apkItems.add(new ApkItem(fileName, outputFile.getAbsolutePath(), false));
                        }
                    }
                }

                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    btnInstall.setEnabled(!apkItems.isEmpty());
                    if (apkItems.isEmpty()) {
                        Toast.makeText(MainActivity.this, "handleSelectedFile not found apk", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "handleSelectedFile found " + apkItems.size() + " apk file", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "handleSelectedFile error", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnInstall.setEnabled(false);
                    Toast.makeText(MainActivity.this, "handleSelectedFile error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要安装未知应用的权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    ///////---------------------
    // 递归删除目录
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }




    public void startInstallService() {
        if (apkItems.isEmpty()) {
            Toast.makeText(this, "startInstallService no apk", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> selectedApks = new ArrayList<>();
        for (ApkItem item : apkItems) {
            if (item.isSelected()) {
                selectedApks.add(item.getFilePath());
            }
        }

        if (selectedApks.isEmpty()) {
            Toast.makeText(this, "startInstallService please select base apk item", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            progressBar.setVisibility(View.VISIBLE);
            btnInstall.setEnabled(false);
            Intent installIntent = new Intent(this, InstallService.class);
            InstallService.setSelectApkFiles(selectedApks);
            startService(installIntent);

            Toast.makeText(this, "installService start...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "installService start fail: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * 从完整路径中提取简单文件名
     * 例如: "asset-slices/obbassets-master.apk" -> "obbassets-master.apk"
     */
    private String getSimpleFileName(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < fullPath.length() - 1) {
            return fullPath.substring(lastSlash + 1);
        }
        return fullPath;
    }

    // 清理资源
    private void cleanup() {
        apkItems.clear();
        if (tempApksDir != null) {
            deleteRecursive(tempApksDir);
        }
        if (tempApksFile != null) {
            tempApksFile.delete();
            tempApksFile = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.getInstance().removeInstallResultListener(this);
        cleanup();
    }

    @Override
    public void onInstallResult(boolean success, String message) {
        progressBar.setVisibility(View.GONE);
        btnInstall.setEnabled(true);
    }
}