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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.del.qr.Message;
import com.google.android.gms.common.api.CommonStatusCodes;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Main activity demonstrating how to pass extra parameters to an activity that
 * reads barcodes.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    // use a compound button so either checkbox or switch widgets work.
    private TextView statusMessage;
    private TextView barcodeValue;

    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = "BarcodeMain";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Check for the camera permission before accessing the storage.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (rc != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission();
        }

        statusMessage = (TextView) findViewById(R.id.status_message);
        barcodeValue = (TextView) findViewById(R.id.barcode_value);

        findViewById(R.id.read_barcode).setOnClickListener(this);
        findViewById(R.id.btn_web_client).setOnClickListener(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.exit_question)
                    .setPositiveButton(R.string.YES, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                finishAffinity();
                            } else {
                                finish();
                            }
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

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.read_barcode: {
                // launch barcode activity.
                Intent intent = new Intent(this, BarcodeCaptureActivity.class);
                startActivityForResult(intent, RC_BARCODE_CAPTURE);
                break;

            }
            case R.id.btn_web_client: {
                Intent intent = new Intent(this, WebClientActivity.class);
                startActivity(intent);
                break;

            }
        }

    }

    /**
     * Called when an activity you launched exits, giving you the requestCode
     * you started it with, the resultCode it returned, and any additional
     * data from it.  The <var>resultCode</var> will be
     * {@link #RESULT_CANCELED} if the activity explicitly returned that,
     * didn't return any result, or crashed during its operation.
     * <p/>
     * <p>You will receive this call immediately before onResume() when your
     * activity is re-starting.
     * <p/>
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param i           An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     * @see #startActivityForResult
     * @see #createPendingResult
     * @see #setResult(int)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent i) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (i != null) {
                    Message data = (Message) i.getSerializableExtra(BarcodeCaptureActivity.BarcodeObject);
                    if (data != null) {
                        statusMessage.setText(R.string.unzip_stage_1);
                        new DownloadImageTask().execute(data);
                    }
                } else {
                    statusMessage.setText(R.string.barcode_failure);
                    Log.d(TAG, "No barcode captured, intent data is null");
                }
            } else {
                statusMessage.setText(String.format(getString(R.string.barcode_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, i);
        }
    }

    private class DownloadImageTask extends AsyncTask<Message, Void, String> {

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
                                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    clipboard.setText(text);
                                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                            "Текст скопирован в буфер обмена",
                                            5000).show();

                                }
                            });
                            return String.format(getString(R.string.unzip_stage_4), m.getName());
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
            if (s != null) {
                statusMessage.setText(s);
            }
        }
    }

    private void requestStoragePermission() {
        Log.w(TAG, "Storage permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, permissions, 2);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions, 2);
            }
        };

        findViewById(R.id.mainTopLayout).setOnClickListener(listener);
        Snackbar.make(getWindow().getDecorView().getRootView(),
                R.string.permission_storage_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

}
