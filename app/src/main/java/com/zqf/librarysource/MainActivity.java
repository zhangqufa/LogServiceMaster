package com.zqf.librarysource;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import com.zqf.logservice.LogService;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONSREQUESTCODE = 101;
    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermission();
    }

    private void requestPermission() {
        int i = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int i1 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int i2 = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        int i3 = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (PackageManager.PERMISSION_DENIED == i || PackageManager.PERMISSION_DENIED == i1 || PackageManager.PERMISSION_DENIED == i2 || PackageManager.PERMISSION_DENIED == i3) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONSREQUESTCODE);
        } else {
            initLogService();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        wLog("onWindowFocusChanged");
    }

    protected void wLog(String msg) {
        if (logService != null) {
            logService.recordLogServiceLog(msg);
        }
    }

    /**
     * 开启记录日志的服务
     */
    protected void initLogService() {
        Intent intent = new Intent(this, LogService.class);
        startService(intent);
        bindService(intent, conn, BIND_AUTO_CREATE);
    }

    protected LogService logService;
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            logService = ((LogService.MyBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            logService = null;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONSREQUESTCODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initLogService();
            } else {
                requestPermission();
            }
        }
    }
}
