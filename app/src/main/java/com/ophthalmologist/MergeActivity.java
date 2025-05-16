package com.ophthalmologist;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.Landmark;
import com.google.android.gms.tasks.Tasks;

public class MergeActivity extends AppCompatActivity {

    private static final int MAX_IMAGES = 9;
    private Bitmap mergedBitmap; // 保存合并后的Bitmap
    private FaceDetector faceDetector; // ML Kit人脸检测器
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

        // 初始化ML Kit人脸检测器
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        faceDetector = FaceDetection.getClient(options);

        btnUploadImages.setOnClickListener(v -> checkStoragePermission());
        btnMergeImages.setOnClickListener(v -> mergeImages());
        btnDownloadResult.setOnClickListener(v -> saveMergedImage());
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestPermissionLauncher.launch("android.permission.READ_EXTERNAL_STORAGE");
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

                        // 使用ML Kit检测人脸
                        InputImage image = InputImage.fromBitmap(bitmap, 0);
                        List<Face> faces = Tasks.await(faceDetector.process(image));

                        Bitmap croppedBitmap = bitmap; // 默认使用原始图片
                        if (!faces.isEmpty()) {
                            Face face = faces.get(0);
                            Landmark leftEye = face.getLandmark(Landmark.LANDMARK_LEFT_EYE);
                            Landmark rightEye = face.getLandmark(Landmark.LANDMARK_RIGHT_EYE);
                            if (leftEye != null && rightEye != null) {
                                // 计算双眼区域（示例范围，可调整）
                                float leftX = leftEye.getPosition().x;
                                float leftY = leftEye.getPosition().y;
                                float rightX = rightEye.getPosition().x;
                                float rightY = rightEye.getPosition().y;

                                int startX = (int) (Math.min(leftX, rightX) - 50);
                                int startY = (int) (Math.min(leftY, rightY) - 50);
                                int endX = (int) (Math.max(leftX, rightX) + 50);
                                int endY = (int) (Math.max(leftY, rightY) + 50);

                                // 确保区域在图片范围内
                                startX = Math.max(0, startX);
                                startY = Math.max(0, startY);
                                endX = Math.min(bitmap.getWidth(), endX);
                                endY = Math.min(bitmap.getHeight(), endY);

                                if (endX > startX && endY > startY) {
                                    croppedBitmap = Bitmap.createBitmap(bitmap, startX, startY, endX - startX, endY - startY);
                                }
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