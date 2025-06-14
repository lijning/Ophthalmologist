package com.ophthalmologist;

import android.os.Bundle;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.os.Build;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {
    private static final String TAG="MainActivity";

    static {
        System.loadLibrary("opencv_java4");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV初始化失败", Toast.LENGTH_SHORT).show();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnMergeEyePhotos = findViewById(R.id.btn_merge_eye_photos);
        btnMergeEyePhotos.setOnClickListener(v -> {
            // 动态判断需要的权限（兼容Android 35+）
            String[] requiredPermissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+（含Android 35）
                requiredPermissions = new String[] {
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            } else { // 旧版本
                requiredPermissions = new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            }

            // 检查存储权限
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    requiredPermissions[0]) == PackageManager.PERMISSION_GRANTED) {
                // 已有权限，启动MergeActivity
                Intent intent = new Intent(MainActivity.this, MergeActivity.class);
                startActivity(intent);
            } else {
                // 请求权限
                ActivityCompat.requestPermissions(MainActivity.this, requiredPermissions, 1001);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予，启动MergeActivity
                Intent intent = new Intent(MainActivity.this, MergeActivity.class);
                startActivity(intent);
            } else {
                // 权限拒绝，提示用户
                Toast.makeText(this, "需要存储权限以继续操作", Toast.LENGTH_SHORT).show();
            }
        }
    }
}