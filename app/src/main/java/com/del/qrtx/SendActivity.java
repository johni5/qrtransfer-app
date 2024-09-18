package com.del.qrtx;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.del.qr.Message;
import com.del.qr.MessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SendActivity extends AppCompatActivity {

    private static final int RC_HANDLE_PERM_READ = 3;
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
            int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (rc != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermission(Manifest.permission.READ_EXTERNAL_STORAGE, RC_HANDLE_PERM_READ);
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).
                        setType("*/*").
                        addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Укажите файл"), RC_READ_FILE);
            }
        });

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


    private void requestStoragePermission(String externalStorage, int requestCode) {
        final String[] permissions = new String[]{externalStorage};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, externalStorage)) {
            ActivityCompat.requestPermissions(this, permissions, requestCode);
            return;
        }
        final Activity thisActivity = this;
        View.OnClickListener listener = view ->
                ActivityCompat.requestPermissions(thisActivity, permissions, requestCode);

        findViewById(R.id.sendRootLayout).setOnClickListener(listener);
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        R.string.permission_storage_open,
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }
}
