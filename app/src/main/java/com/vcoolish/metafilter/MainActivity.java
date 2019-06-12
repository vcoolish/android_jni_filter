package com.vcoolish.metafilter;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    String resultsFileName = "results.log";
    String lastFile = "last_file";
    int PERMISSION_REQUEST = 100;
    String DEFAULTS = "Defaults";

    ArrayList<String> urls = new ArrayList<>();

    AppCompatButton downloadButton;
    AppCompatEditText etUrl;
    AppCompatEditText etFilter;
    ProgressBar progressBar;

    SharedPreferences sharedPreds;
    TextItemsRecyclerAdapter adapter;
    BroadcastReceiver onDownloadComplete = null;

    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreds = getSharedPreferences(DEFAULTS, Context.MODE_PRIVATE);
        initViews();
        setOnClickListeners();

        setupListView();
    }

    private void initViews() {
        etUrl = findViewById(R.id.etUrl);
        etFilter = findViewById(R.id.etFilter);
        downloadButton = findViewById(R.id.getContentButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setOnClickListeners() {
        View.OnClickListener btnClick = v -> {
            String[] perms = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
            if (etUrl.getText() != null) {
                if (ContextCompat.checkSelfPermission(
                        MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                                MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            MainActivity.this,
                            perms,
                            PERMISSION_REQUEST
                    );
                } else {
                    getContentButtonPressed();
                }
            }
        };

        downloadButton.setOnClickListener(btnClick);
    }

    private void getContentButtonPressed() {
        progressBar.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        Editable urlText = etUrl.getText();
        if (urlText != null) {
            String url = urlText.toString();
            downloadFile(url);
        }
    }

    private void setupListView() {
        RecyclerView recyclerView = findViewById(R.id.itemsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TextItemsRecyclerAdapter(this, urls);
        recyclerView.setAdapter(adapter);
    }

    private void downloadFile(final String urlString) {
        new Thread(() -> {
            try {
                enqeueFile(urlString);
            } catch (Exception e) {
                MainActivity.this.runOnUiThread(this::hideProgress);
                e.printStackTrace();
            }
        }).start();
    }

    void registerOnComplete(String downloadsDir) {
        if (onDownloadComplete != null) {
            unregisterReceiver(onDownloadComplete);
        }
        onDownloadComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                parseFile(downloadsDir);
            }
        };
        registerReceiver(onDownloadComplete, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void enqeueFile(String urlString) {
        DownloadManager downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(urlString);
        String filename = uri.getLastPathSegment();
        String filePath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + File.separator
                + filename;
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle(uri.getLastPathSegment());
        request.setDescription(getString(R.string.downloading));
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );

        deleteIfExists(filePath);
        request.setDestinationInExternalFilesDir(
                MainActivity.this, Environment.DIRECTORY_DOWNLOADS, filename);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        request.setVisibleInDownloadsUi(false);
        request.allowScanningByMediaScanner();

        registerOnComplete(filePath);
        sharedPreds.edit().putLong(lastFile, downloadmanager.enqueue(request)).apply();
    }

    private void deleteIfExists(String filePath) {
        File myFile = new File(filePath);
        if(myFile.exists()) {
            myFile.delete();
        }
    }

    private void parseFile(String downloadsDir) {
        MainActivity.this.runOnUiThread(this::clearLines);
        int filtered = filterFromJNI(
                etFilter.getText().toString(),
                downloadsDir
        );
        if (filtered != 0) {
            MainActivity.this.runOnUiThread(() -> toast(getString(R.string.error)));
        }
        hideProgress();
    }

    public void messageMe(String text) {
        try {
            addLine(text);

            File file = new File(getExternalCacheDir(), resultsFileName);
            FileWriter writer = new FileWriter(file);
            writer.append(text);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            hideProgress();
            e.printStackTrace();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST && resultCode == RESULT_OK) {
            getContentButtonPressed();
        }
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        downloadButton.setEnabled(true);
    }

    private void clearLines() {
        urls.clear();
        adapter.notifyDataSetChanged();
    }

    private void addLine(String text) {
        urls.add(text);
        adapter.notifyDataSetChanged();
    }

    public native int filterFromJNI(String filter, String path);
}

