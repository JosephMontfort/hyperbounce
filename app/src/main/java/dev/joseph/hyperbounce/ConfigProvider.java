package dev.joseph.hyperbounce;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ConfigProvider extends ContentProvider {
    private static long lastStartupTime = 0;
    private static int crashLoopCounter = 0;

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("getConfig".equals(method)) {
            Bundle b = new Bundle();
            try {
                File file = new File(getContext().getFilesDir(), "config.json");
                if (file.exists()) {
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();
                    b.putString("config_json", new String(data, "UTF-8"));
                }
            } catch (Exception e) {}
            return b;
        } else if ("systemUiStartup".equals(method)) {
            Bundle b = new Bundle();
            long now = System.currentTimeMillis();
            if (now - lastStartupTime < 25000) {
                crashLoopCounter++;
                if (crashLoopCounter >= 2) {
                    try {
                        File f = new File(getContext().getFilesDir(), "safemode.flag");
                        if (!f.exists()) f.createNewFile();
                    } catch (Exception e) {}
                }
            } else {
                crashLoopCounter = 1;
            }
            lastStartupTime = now;
            
            File f = new File(getContext().getFilesDir(), "safemode.flag");
            b.putBoolean("safeMode", f.exists());
            return b;
        } else if ("systemUiHealthy".equals(method)) {
            crashLoopCounter = 0;
            return new Bundle();
        } else if ("isSafeMode".equals(method)) {
            Bundle b = new Bundle();
            File f = new File(getContext().getFilesDir(), "safemode.flag");
            b.putBoolean("safeMode", f.exists());
            return b;
        } else if ("setSafeMode".equals(method)) {
            try {
                File f = new File(getContext().getFilesDir(), "safemode.flag");
                if ("true".equals(arg)) {
                    if (!f.exists()) f.createNewFile();
                } else {
                    if (f.exists()) f.delete();
                }
            } catch (Exception e) {}
            return new Bundle();
        }
        return null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
