package com.del.qrt;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.del.qr.Message;
import com.del.qr.MessageEncoder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SendActivity extends AppCompatActivity {

    private static final int RC_READ_FILE = 10;

    private ImageView imageView;
    private TextView imageIndex;
    private RelativeLayout rootView;
    private final List<Bitmap> images = new ArrayList<>();
    private int currentImageIndex = 0;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isPaused = new AtomicBoolean();
    private Runnable showNextImage;
    private ImageButton btnPlay;
    private boolean isHeld = false;
    private boolean isHoldOn = false;

    final private ActivityResultLauncher<String[]> requestMultiPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        for (String permission : result.keySet()) {
                            if (!Boolean.TRUE.equals(result.getOrDefault(permission, false))) {
                                Toast.makeText(this, R.string.no_any_permission, Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        openFileToSend();
                    });

    private void checkStorageReadPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            boolean readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!readStoragePermission) {
                String[] permissions = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, R.string.permission_storage_rationale, Toast.LENGTH_SHORT).show();
                }
                requestMultiPermissionLauncher.launch(permissions);
                return;
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            boolean readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!readStoragePermission) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, R.string.permission_storage_rationale, Toast.LENGTH_SHORT).show();
                }
                requestMultiPermissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
                return;
            }

        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            boolean readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
            boolean writeStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
            if (!readStoragePermission || !writeStoragePermission) {
                String[] permissions = new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                };
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_IMAGES)) {
                    Toast.makeText(this, R.string.permission_storage_rationale, Toast.LENGTH_SHORT).show();
                }
                requestMultiPermissionLauncher.launch(permissions);
                return;
            }

        } else {
            boolean readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    == PackageManager.PERMISSION_GRANTED;
            if (!readStoragePermission) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                    Toast.makeText(this, R.string.permission_storage_rationale, Toast.LENGTH_SHORT).show();
                }
                requestMultiPermissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED});
                return;
            }
        }
        openFileToSend();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        rootView = findViewById(R.id.sendRootLayout);
        imageView = findViewById(R.id.qrImageView);
        imageIndex = findViewById(R.id.imageIndex);

        Button btnPrevious = findViewById(R.id.btnPrevious);
        btnPlay = findViewById(R.id.btnPlay);
        Button btnNext = findViewById(R.id.btnNext);
        ImageButton btnAdd = findViewById(R.id.btnAdd);
        ImageButton btnBack = findViewById(R.id.btnBack);

        showNextImage = new Runnable() {
            @Override
            public void run() {
                if (!images.isEmpty()) {
                    renderImage();
                    if (!isPaused.get()) {
                        currentImageIndex = (currentImageIndex + 1) % images.size();
                        handler.postDelayed(this, 500);
                    }
                }
            }
        };

        btnPlay.setOnClickListener(v -> {
            if (images.isEmpty()) return;
            if (isPaused.get()) {
                onBtnPlay();
                handler.post(showNextImage);
            } else {
                onBtnPause();
            }
        });

        Runnable heldRunnablePrev = new Runnable() {
            @Override
            public void run() {
                if (images.isEmpty()) return;
                if (isHeld) {
                    isHoldOn = true;
                    onBtnPause();
                    currentImageIndex = (currentImageIndex - 1 + images.size()) % images.size();
                    renderImage();
                    handler.postDelayed(this, 100);
                }
            }
        };

        btnPrevious.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isHeld = true;
                    isHoldOn = false;
                    handler.postDelayed(heldRunnablePrev, 1000);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (images.isEmpty()) return false;
                    isHeld = false;
                    handler.removeCallbacks(heldRunnablePrev);
                    if (!isHoldOn) {
                        onBtnPause();
                        currentImageIndex = (currentImageIndex - 1 + images.size()) % images.size();
                        renderImage();
                    }
                    return true;
            }
            return false;
        });

        Runnable heldRunnableNext = new Runnable() {
            @Override
            public void run() {
                if (images.isEmpty()) return;
                if (isHeld) {
                    isHoldOn = true;
                    onBtnPause();
                    currentImageIndex = (currentImageIndex + 1) % images.size();
                    renderImage();
                    handler.postDelayed(this, 100);
                }
            }
        };

        btnNext.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isHeld = true;
                    isHoldOn = false;
                    handler.postDelayed(heldRunnableNext, 1000);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (images.isEmpty()) return false;
                    isHeld = false;
                    handler.removeCallbacks(heldRunnableNext);
                    if (!isHoldOn) {
                        onBtnPause();
                        currentImageIndex = (currentImageIndex + 1) % images.size();
                        renderImage();
                    }
                    return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        btnAdd.setOnClickListener(v -> {
            checkStorageReadPermissions();
        });

    }

    private void openFileToSend() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).
                setType("*/*").
                addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Укажите файл"), RC_READ_FILE);
    }

    private void onBtnPause() {
        isPaused.set(true);
        btnPlay.setImageResource(R.drawable.ic_baseline_play_arrow_24);
    }

    private void onBtnPlay() {
        isPaused.set(false);
        btnPlay.setImageResource(R.drawable.ic_baseline_pause_24);
    }

    private void renderImage() {
        imageView.setImageBitmap(images.get(currentImageIndex));
        imageIndex.setText(String.format("%s:%s", currentImageIndex + 1, images.size()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused.set(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_READ_FILE && resultCode == RESULT_OK && data != null) {
            try {
                images.clear();
                Uri uri = data.getData();
                byte[] bytes = Utils.readFile(this, uri);
                String fName = Utils.getFileName(this, uri);
                Log.i(getClass().getName(), String.format("Sending file '%s'", fName));
                Message m = new Message(fName, bytes);
                executor.execute(() -> {
                    try {
                        AtomicInteger index = new AtomicInteger(1);
                        List<String> list = MessageEncoder.code(m);
                        for (String s : list) {
                            images.add(Utils.getQRCode(s));
                            handler.post(() -> {
                                imageIndex.setText(String.format("%s/%s", index.getAndIncrement(), list.size()));
                            });
                        }
                        isPaused.set(true);
                        currentImageIndex = 0;
                        handler.post(showNextImage);
                    } catch (Exception e) {
                        Utils.showError(rootView, e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                Utils.showError(rootView, e.getMessage(), e);
            }
        }
    }
}
