package com.ophthalmologist;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import org.opencv.objdetect.CascadeClassifier;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.Rect;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

/** @noinspection CallToPrintStackTrace*/
public class MergeActivity extends AppCompatActivity {

    private static final int MAX_IMAGES = 9;
    private Bitmap mergedBitmap; // 保存合并后的Bitmap
    private CascadeClassifier eyeCascade; // OpenCV眼睛检测器
    private CascadeClassifier faceCascade; // 新增：OpenCV人脸检测器
    private GridView gvImagePreview;
    private ProgressBar pbMerging;
    private Button btnUploadImages, btnMergeImages, btnDownloadResult;
    private List<Uri> selectedImageUris = new ArrayList<>();
    private List<Map<String, Object>> imageItems = new ArrayList<>();
    private SimpleAdapter imageAdapter;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            if (count > MAX_IMAGES) {
                                Toast.makeText(MergeActivity.this, "最多选择9张图片", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            for (int i = 0; i < count; i++) {
                                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                                selectedImageUris.add(imageUri);
                            }
                        } else if (data.getData() != null) {
                            selectedImageUris.add(data.getData());
                        }
                        updateImagePreview();
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openImagePicker();
                } else {
                    Toast.makeText(this, "需要存储权限以选择图片", Toast.LENGTH_SHORT).show();
                }
            });

    private TextView tvImageCount; // 新增：图片数量显示控件

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge);

        gvImagePreview = findViewById(R.id.gv_image_preview);
        pbMerging = findViewById(R.id.pb_merging);
        btnUploadImages = findViewById(R.id.btn_upload_images);
        btnMergeImages = findViewById(R.id.btn_merge_images);
        btnDownloadResult = findViewById(R.id.btn_download_result);
        
        // 新增：初始化图片数量显示控件（关键修复）
        tvImageCount = findViewById(R.id.tv_image_count);

        imageAdapter = new SimpleAdapter(this,
        imageItems,
        R.layout.item_image,
        new String[] {
                "image" },
        new int[] { R.id.iv_item_image });
        gvImagePreview.setAdapter(imageAdapter);

        // 初始化OpenCV眼睛检测器（需提前将haarcascade_eye.xml放入assets目录）
        try {
            InputStream is = getAssets().open("haarcascade_eye.xml");
            File cascadeDir = getDir("cascade", MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "haarcascade_eye.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            eyeCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (eyeCascade.empty()) {
                Toast.makeText(this, "未能加载眼睛检测模型", Toast.LENGTH_SHORT).show();
            }
            cascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // 新增：初始化OpenCV人脸检测器
        try {
            InputStream faceIs = getAssets().open("haarcascade_frontalface_default.xml");
            File faceCascadeDir = getDir("face_cascade", MODE_PRIVATE);
            File faceCascadeFile = new File(faceCascadeDir, "haarcascade_frontalface_default.xml");
            FileOutputStream faceOs = new FileOutputStream(faceCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = faceIs.read(buffer)) != -1) {
                faceOs.write(buffer, 0, bytesRead);
            }
            faceIs.close();
            faceOs.close();

            faceCascade = new CascadeClassifier(faceCascadeFile.getAbsolutePath());
            if (faceCascade.empty()) {
                Toast.makeText(this, "未能加载人脸检测模型", Toast.LENGTH_SHORT).show();
            }
            faceCascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载人脸模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        btnUploadImages.setOnClickListener(v -> checkStoragePermission());
        btnMergeImages.setOnClickListener(v -> mergeImages());
        btnDownloadResult.setOnClickListener(v -> saveMergedImage());
    }

    private void checkStoragePermission() {
        String requiredPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            requiredPermission = "android.permission.READ_MEDIA_IMAGES";
        } else { // API < 33
            requiredPermission = "android.permission.READ_EXTERNAL_STORAGE";
        }

        if (ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestPermissionLauncher.launch(requiredPermission);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }

    private void updateImagePreview() {
        List<Bitmap> bitmaps = new ArrayList<>();
        for (Uri uri : selectedImageUris) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                // 解码原始图片
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                if (originalBitmap != null) {
                    // 关键修改：缩放到1280x720（16:9比例）
//                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 1280, 720, true);
//                    bitmaps.add(scaledBitmap);
                    bitmaps.add(originalBitmap);
                    // 释放原始Bitmap内存（可选优化）
                    originalBitmap.recycle();
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "图片解码失败: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show());
                }
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "加载图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
        imageItems.clear();
        for (Bitmap bitmap : bitmaps) {
            Map<String, Object> item = new HashMap<>();
            item.put("image", bitmap);
            imageItems.add(item);
        }
        imageAdapter.notifyDataSetChanged();
    }

    private Bitmap processBitmap(Bitmap bitmap, int idx) {
        // 1. 检测人脸
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.equalizeHist(grayMat, grayMat);
    
        // 检测人脸
        MatOfRect faces = new MatOfRect();
        if (faceCascade != null && !faceCascade.empty()) {
            faceCascade.detectMultiScale(grayMat, faces, 1.1, 3, 0, new Size(50, 50), new Size());
        }
        List<Rect> faceList = faces.toList();
        if (faceList.isEmpty()) {
            Toast.makeText(this, String.format(Locale.CHINA, "第%d张未检测到人脸", idx), Toast.LENGTH_SHORT).show();
            return bitmap; // 未检测到人脸，返回原图
        }
    
        // 取第一个人脸区域
        Rect faceRect = faceList.get(0);
        Mat faceMat = new Mat(grayMat, faceRect); // 截取人脸区域的Mat
    
        // 2. 在人脸区域内检测眼睛
        MatOfRect eyes = new MatOfRect();
        if (eyeCascade != null && !eyeCascade.empty()) {
            eyeCascade.detectMultiScale(faceMat, eyes, 1.1, 2, 0, new Size(30, 30), new Size());
        }
        List<Rect> eyeList = eyes.toList();
        if (eyeList.size() < 2) {
            Toast.makeText(this,  String.format(Locale.CHINA, "第%d张未检测到双眼", idx), Toast.LENGTH_SHORT).show();
            return bitmap; // 未检测到至少2只眼睛，返回原图
        }
    
        // 3. 计算左右眼中间点（假设前两个是左右眼）
        Rect leftEye = eyeList.get(0);
        Rect rightEye = eyeList.get(1);
        // 注意：人脸区域是原图的子区域，需要转换为原图坐标
        int leftEyeX = faceRect.x + leftEye.x + leftEye.width / 2;
        int leftEyeY = faceRect.y + leftEye.y + leftEye.height / 2;
        int rightEyeX = faceRect.x + rightEye.x + rightEye.width / 2;
        int rightEyeY = faceRect.y + rightEye.y + rightEye.height / 2;
    
        // 中间点坐标
        int centerX = (leftEyeX + rightEyeX) / 2;
        int centerY = (leftEyeY + rightEyeY) / 2;
    
        // 4. 计算2:1长宽比的截取区域（假设宽度为目标宽度，高度为宽度/2）
        int targetWidth = Math.min(bitmap.getWidth(), faceRect.width); // 以人脸宽度为基准
        int targetHeight = targetWidth / 2;
    
        // 确保截取区域不超出原图边界
        int startX = Math.max(0, centerX - targetWidth / 2);
        int startY = Math.max(0, centerY - targetHeight / 2);
        int endX = Math.min(bitmap.getWidth(), startX + targetWidth);
        // 调整宽度保持2:1比例（可能因边界限制调整）
        targetWidth = endX - startX;
        targetHeight = targetWidth / 2;
        int endY = Math.min(bitmap.getHeight(), startY + targetHeight);
    
        if (endX > startX && endY > startY) {
            return Bitmap.createBitmap(bitmap, startX, startY, endX - startX, endY - startY);
        }
        return bitmap;
    }

    private void mergeImages() {
        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, "请选择至少1张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        pbMerging.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                int size = (int) getResources().getDisplayMetrics().density * 100;
                // 调整画布高度为 3行 × 每行高度(size/2)，总高度为 size*1.5
                mergedBitmap = Bitmap.createBitmap(size * 3, (size / 2) * 3, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(mergedBitmap);

                for (int i = 0; i < selectedImageUris.size() && i < MAX_IMAGES; i++) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(selectedImageUris.get(i));
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap == null) continue;

                        Bitmap croppedBitmap = processBitmap(bitmap, i);

                        // 关键修改：缩放为 2:1 比例（宽度size，高度size/2）
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, size, size / 2, true);
                        
                        // 调整y坐标计算（每行高度为size/2）
                        int x = (i % 3) * size;
                        int y = (i / 3) * (size / 2);
                        
                        canvas.drawBitmap(scaledBitmap, x, y, null);

                        if (inputStream != null) inputStream.close();
                    } catch (Exception e) {
                        Toast.makeText(this, String.format(Locale.CHINA, "第%d张处理失败", i), Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
                runOnUiThread(() -> {
                    pbMerging.setVisibility(View.GONE);
                    btnDownloadResult.setVisibility(View.VISIBLE);
                    ImageView ivMergedResult = findViewById(R.id.iv_merged_result);
                    ivMergedResult.setImageBitmap(mergedBitmap);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    pbMerging.setVisibility(View.GONE);
                    Toast.makeText(MergeActivity.this, "合并失败", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveMergedImage() {
        if (mergedBitmap == null) {
            Toast.makeText(this, "无合并结果可保存", Toast.LENGTH_SHORT).show();
            return;
        }
        // 保存到相册
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(storageDir, "merged_eye_photos_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos); // 实际保存Bitmap
            Toast.makeText(this, "保存成功：" + file.getPath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }
}