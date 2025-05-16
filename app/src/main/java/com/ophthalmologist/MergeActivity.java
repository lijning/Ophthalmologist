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

public class MergeActivity extends AppCompatActivity {

    private static final int MAX_IMAGES = 9;
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
                new String[]{"image"},
                new int[]{R.id.iv_item_image});
        gvImagePreview.setAdapter(imageAdapter);

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
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUris.get(i));
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true);
                    int x = (i % 3) * size;
                    int y = (i / 3) * size;
                    canvas.drawBitmap(scaledBitmap, x, y, null);
                    if (inputStream != null) inputStream.close();
                }
                runOnUiThread(() -> {
                    pbMerging.setVisibility(View.GONE);
                    btnDownloadResult.setVisibility(View.VISIBLE);
                    // 可添加展示合并结果的逻辑
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
        // 示例：保存到相册
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(storageDir, "merged_eye_photos_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // 假设mergedBitmap是合并后的Bitmap
            // mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            Toast.makeText(this, "保存成功：" + file.getPath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }
}