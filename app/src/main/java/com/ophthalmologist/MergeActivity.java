package com.ophthalmologist;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.opencv.android.OpenCVLoader;
import java.util.ArrayList;

public class MergeActivity extends AppCompatActivity {
    private static final int PICK_IMAGES = 1;
    private RecyclerView recyclerView;
    private ImageAdapter adapter;
    private ArrayList<Uri> imageUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.merge_layout);
        
        // 初始化OpenCV
        OpenCVLoader.initDebug();

        recyclerView = findViewById(R.id.grid_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageAdapter(imageUris, this::deleteImage);
        recyclerView.setAdapter(adapter);

        // 启动图片选择
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGES) {
            if (data.getClipData() != null) {
                int count = Math.min(data.getClipData().getItemCount(), 9);
                for (int i = 0; i < count; i++) {
                    imageUris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                imageUris.add(data.getData());
            }
            adapter.notifyDataSetChanged();
            new ImageProcessingTask().execute();
        }
    }

    private void deleteImage(int position) {
        imageUris.remove(position);
        adapter.notifyItemRemoved(position);
    }

    private class ImageProcessingTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MergeActivity.this);
            progressDialog.setMessage("Processing images...");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // OpenCV图像处理逻辑
            for (int i = 0; i < imageUris.size(); i++) {
                // 实现眼部区域检测
                publishProgress((i * 100) / imageUris.size());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            Toast.makeText(MergeActivity.this, "Processing completed", Toast.LENGTH_SHORT).show();
        }
    }
}