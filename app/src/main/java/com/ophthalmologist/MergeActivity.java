package com.ophthalmologist;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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

public class MergeActivity extends AppCompatActivity {

    private static final int MAX_IMAGES = 9;
    private Bitmap mergedBitmap; // 保存合并后的Bitmap
    private CascadeClassifier eyeCascade; // OpenCV眼睛检测器
    private GridView gvImagePreview;
    private ProgressBar pbMerging;
    private Button btnUploadImages, btnMergeImages, btnDownloadResult;
    private List<Uri> selectedImageUris = new ArrayList<>();
    private SimpleAdapter imageAdapter;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge);

        gvImagePreview = findViewById(R.id.gv_image_preview);
        pbMerging = findViewById(R.id.pb_merging);
        btnUploadImages = findViewById(R.id.btn_upload_images);
        btnMergeImages = findViewById(R.id.btn_merge_images);
        btnDownloadResult = findViewById(R.id.btn_download_result);

        imageAdapter = new SimpleAdapter(this,
                new ArrayList<>(),
                R.layout.item_image,
                new String[]{
"image"},
                new int[]{R.id.iv_item_image});
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
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                bitmaps.add(bitmap);
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<java.util.HashMap<String, Bitmap>> data = new ArrayList<>();
        for (Bitmap bm : bitmaps) {
            java.util.HashMap<String, Bitmap> map = new java.util.HashMap<>();
            map.put("image", bm);
            data.add(map);
        }
        imageAdapter = new SimpleAdapter(this,
                data,
                R.layout.item_image,
                new String[]{"image"},
                new int[]{R.id.iv_item_image}) {
            public void setViewImage(ImageView v, Bitmap bm) {
                v.setImageBitmap(bm);
            }
        };
        gvImagePreview.setAdapter(imageAdapter);
    }

    private void mergeImages() {
        if (selectedImageUris.size() < 1) {
            Toast.makeText(this, "请选择至少1张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        pbMerging.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                int size = (int) getResources().getDisplayMetrics().density * 100;
                Bitmap mergedBitmap = Bitmap.createBitmap(size * 3, size * 3, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(mergedBitmap);

                for (int i = 0; i < selectedImageUris.size() && i < MAX_IMAGES; i++) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(selectedImageUris.get(i));
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap == null) continue;

                        // 使用OpenCV检测眼睛
                        Mat mat = new Mat();
                        Utils.bitmapToMat(bitmap, mat); // Bitmap转Mat
                        Mat grayMat = new Mat();
                        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY);
                        Imgproc.equalizeHist(grayMat, grayMat);

                        MatOfRect eyes = new MatOfRect();
                        eyeCascade.detectMultiScale(grayMat, eyes, 1.1, 2, 0, new Size(30, 30), new Size());

                        List<Rect> eyeList = eyes.toList();
                        Bitmap croppedBitmap = bitmap;
                        if (!eyeList.isEmpty()) {
                            // 取第一个检测到的眼睛区域（示例逻辑，可根据需求调整）
                            Rect eyeRect = eyeList.get(0);
                            int startX = Math.max(0, eyeRect.x - 50);
                            int startY = Math.max(0, eyeRect.y - 50);
                            int endX = Math.min(bitmap.getWidth(), eyeRect.x + eyeRect.width + 50);
                            int endY = Math.min(bitmap.getHeight(), eyeRect.y + eyeRect.height + 50);

                            if (endX > startX && endY > startY) {
                                croppedBitmap = Bitmap.createBitmap(bitmap, startX, startY, endX - startX, endY - startY);
                            }
                        }

                        // 缩放截取后的区域
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, size, size, true);
                        int x = (i % 3) * size;
                        int y = (i / 3) * size;
                        canvas.drawBitmap(scaledBitmap, x, y, null);

                        if (inputStream != null) inputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                runOnUiThread(() -> {
                    pbMerging.setVisibility(View.GONE);
                    btnDownloadResult.setVisibility(View.VISIBLE);
                    // 展示合并结果到ImageView
                    ImageView ivMergedResult = findViewById(R.id.iv_merged_result);
                    ivMergedResult.setImageBitmap(mergedBitmap);
                    // 保存合并后的Bitmap到成员变量
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