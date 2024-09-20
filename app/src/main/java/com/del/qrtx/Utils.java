package com.del.qrtx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

public final class Utils {

    private Utils() {
    }

    @SuppressLint("Range")
    public static String getFileName(Context ctx, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public static Bitmap getQRCode(String text) throws WriterException {
        int qrcodeWidth = 400;
        int qrcodeHeight = 400;
        Map<EncodeHintType, String> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, "L");
        BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, qrcodeWidth, qrcodeHeight, hints);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        return barcodeEncoder.createBitmap(bitMatrix);
    }

    public static byte[] readFile(Context ctx, Uri uri) throws IOException {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        ReadableByteChannel src = Channels.newChannel(is);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel dest = Channels.newChannel(out);
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        transfer(src, dest, buffer);
        return out.toByteArray();
    }

    private static void transfer(ReadableByteChannel src,
                                 WritableByteChannel dest,
                                 ByteBuffer buffer) throws IOException {
        try {
            while (src.read(buffer) != -1) {
                buffer.flip();
                dest.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
        } finally {
            safeClose(dest);
            safeClose(src);
        }
    }

    public static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public static void showError(View view, String text, Exception e) {
        String message = "";
        if (text != null) {
            message = text;
        }
        if (e != null) {
            if (message.length() > 0) message += ": ";
            message += e.getMessage();
        }
        Log.e("ERROR", message, e);
        Snackbar.make(view, message, 5000).show();
    }
}
