/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.del.qrt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.del.qr.Message;
import com.del.qr.MessageEncoder;
import com.del.qr.Part;
import com.del.qrt.camera.CameraSource;
import com.del.qrt.camera.CameraSourcePreview;
import com.del.qrt.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main activity demonstrating how to pass extra parameters to an activity that
 * reads barcodes.
 */
public class MainActivity extends AppCompatActivity implements BarcodeGraphicTracker.BarcodeUpdateListener, View.OnClickListener {

    private static final String TAG = "QR-transfer-main";

    // use a compound button so either checkbox or switch widgets work.
    private TextView uploadInfoMessage;
    private ProgressBar progressBar;

    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_PERM = 2;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private final Map<Integer, String> bodyTotal = new ConcurrentHashMap<>();
    private int countTotal = 0;
    private String name;
    private transient boolean ready;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(getString(R.string.title_activity_main) + " V" + getString(R.string.version));
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.graphicOverlay);
        uploadInfoMessage = findViewById(R.id.upload_info);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setMax(0);
        progressBar.setProgress(0);
        findViewById(R.id.bHelp).setOnClickListener(this);

        checkPermissionsAndRun();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bHelp: {
                Intent intent = new Intent(this, WebClientActivity.class);
                startActivity(intent);
                break;

            }
        }

    }

    private void checkPermissionsAndRun() {
        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        // Check for the camera permission before accessing the storage.  If the
        // permission is not granted yet, request permission.
        rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (rc != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
            return;
        }

        createCameraSource();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_header)
                    .setMessage(R.string.exit_question)
                    .setPositiveButton(R.string.YES, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finishAffinity();
                        }
                    })
                    .setNegativeButton(R.string.NO, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    }).show();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void requestStoragePermission() {
        Log.w(TAG, "Storage permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_PERM);
            }
        };

        findViewById(R.id.mainTopLayout).setOnClickListener(listener);
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        R.string.permission_storage_rationale,
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_PERM);
            }
        };

        findViewById(R.id.mainTopLayout).setOnClickListener(listener);
        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     * <p>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource() {

        Context context = getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, this);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        builder = builder.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        mCameraSource = builder.build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndRun();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_header)
                .setMessage(R.string.no_any_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
                ready = true;
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void onBarcodeDetected(Barcode barcode) {
        //do something with barcode data returned
        if (ready) new ReadBarcodeTask().execute(barcode);

    }

    @SuppressLint("StaticFieldLeak")
    private class ReadBarcodeTask extends AsyncTask<Barcode, Void, Message> {

        public ReadBarcodeTask() {
            super();
        }

        @Override
        protected Message doInBackground(Barcode... s) {
            if (s != null && s.length > 0) {
                String hex = s[0].rawValue;
                if (hex != null && hex.length() > 7) {
                    try {
                        Part p = MessageEncoder.encodeStr(hex);
                        if (p != null) {
                            int index = p.getIndex();
                            int count = p.getSize();
                            if (index == 0 && (countTotal == 0 || !p.getBody().equals(name))) {
                                countTotal = count + 1;
                                name = p.getBody();
                                bodyTotal.clear();
                            }
                            if (index < countTotal && !bodyTotal.containsKey(index)) {
                                Log.i(TAG, "*** Detect part " + index + " of " + count + " ***");
                                bodyTotal.put(index, hex);
                                if (bodyTotal.size() == countTotal) {
                                    Log.i(TAG, "*** FINISH ***");
                                    ready = false;
                                    return MessageEncoder.encode(bodyTotal.values());
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "*** Bad part: " + hex + "  ***", e);
                    }

                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Message data) {
            if (data != null) {
                // Process received file
                uploadInfoMessage.setText("");
                progressBar.setProgress(bodyTotal.size());
                new SavePackageTask().execute(data);
                countTotal = 0;
                bodyTotal.clear();
            }
            if (countTotal > 0) {
                int waitIndex = 0;
                while (waitIndex <= countTotal) {
                    if (!bodyTotal.containsKey(waitIndex++)) break;
                }
                String _name = "-";
                try {
                    _name = new String(name.getBytes("ISO-8859-15"), StandardCharsets.UTF_8);
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "UnsupportedEncodingException", e);
                }
                uploadInfoMessage.setText(String.format("%s получено %s из %s жду %s", _name, bodyTotal.size(), countTotal, waitIndex));
                progressBar.setMax(countTotal);
                progressBar.setProgress(bodyTotal.size());
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SavePackageTask extends AsyncTask<Message, Void, String> {

        public SavePackageTask() {
            super();
        }

        @Override
        protected String doInBackground(Message... s) {
            if (s != null && s.length > 0) {
                Message m = s[0];
                if (m != null) {
                    try {
                        m.unzip();
                        if (m.isClipboard()) {
                            Handler mainThreadHandler = new Handler(Looper.getMainLooper());
                            final String text = m.getTextUTF();
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Transfer", text));
                                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                            "Текст скопирован в буфер обмена",
                                            5000).show();

                                }
                            });
                            return "Текст получен и будет скопирован в буфер обмена";
                        } else {
                            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            File file = new File(directory, m.getName());
                            if (file.exists() || file.createNewFile()) {
                                FileOutputStream fout = new FileOutputStream(file);
                                fout.write(m.getBody());
                                fout.flush();
                                fout.close();
                                return String.format(getString(R.string.unzip_stage_4), file.getAbsolutePath());
                            } else {
                                return "Не удалось сохранить файл";
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Extract data error", e);
                        return "Не удалось сохранить файл: " + e.getMessage();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (s != null && s.length() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.dialog_header)
                        .setMessage(s)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ready = true;
                                progressBar.setProgress(0);
                                name = null;
                            }
                        })
                        .show();
            }
        }
    }


}
