package com.recorder.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 102;

    private ListView listViewRecordings;
    private TextView tvStatus, tvPlayingName;
    private View playerLayout;
    private Button btnStopPlay, btnStart;

    private List<File> recordingFilesList = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;
    private List<String> fileNamesList = new ArrayList<>();
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        listViewRecordings = findViewById(R.id.listViewRecordings);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayingName = findViewById(R.id.tvPlayingName);
        playerLayout = findViewById(R.id.playerLayout);
        btnStopPlay = findViewById(R.id.btnStopPlay);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileNamesList);
        listViewRecordings.setAdapter(listAdapter);

        btnStart.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                checkOverlayAndBatterySettings();
            }
        });

        listViewRecordings.setOnItemClickListener((parent, view, position, id) -> {
            if (position < recordingFilesList.size()) {
                playRecording(recordingFilesList.get(position));
            }
        });

        btnStopPlay.setOnClickListener(v -> stopPlayback());

        updateStatusText();
        loadSavedRecordings();
    }

    private void updateStatusText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasOverlay = Settings.canDrawOverlays(this);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean ignoringBattery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            
            if (hasOverlay && ignoringBattery) {
                tvStatus.setText("حالة الخدمة: جاهز تماماً للعمل والمراقبة");
                tvStatus.setTextColor(0xFF4CAF50);
            } else {
                tvStatus.setText("حالة الخدمة: بحاجة لضبط الإعدادات والصلاحيات (اضغط الزر)");
                tvStatus.setTextColor(0xFFFF3D00);
            }
        }
    }

    private void loadSavedRecordings() {
        recordingFilesList.clear();
        fileNamesList.clear();

        File sampleDir = new File(getExternalFilesDir(null), "MyRecordings");
        if (sampleDir.exists()) {
            File[] files = sampleDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".mp4")) {
                        recordingFilesList.add(file);
                        long dateMs = file.lastModified();
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                        String sizeStr = String.format("%.2f MB", (double)file.length() / (1024 * 1024));
                        fileNamesList.add("تسجيل (" + sdf.format(new java.util.Date(dateMs)) + ") - " + sizeStr);
                    }
                }
            }
        }

        if (fileNamesList.isEmpty()) {
            fileNamesList.add("لا توجد مكالمات مسجلة محفوظة حالياً.");
        }
        listAdapter.notifyDataSetChanged();
    }

    private void playRecording(File file) {
        stopPlayback();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            playerLayout.setVisibility(View.VISIBLE);
            tvPlayingName.setText("جاري تشغيل: " + file.getName());
            
            mediaPlayer.setOnCompletionListener(mp -> stopPlayback());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تشغيل تسجيل المكالمة: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
        playerLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedRecordings();
        updateStatusText();
    }

    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> listPermissionsNeeded = new java.util.ArrayList<>();
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }

            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    private void checkOverlayAndBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            Toast.makeText(this, "يرجى تفعيل صلاحية الظهور فوق التطبيقات لتفعيل الزر العائم", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
                Toast.makeText(this, "يرجى استبعاد التطبيق من تحسينات البطارية للعمل المستمر في الخلفية", Toast.LENGTH_LONG).show();
                return;
            }
        }

        startRecordingService();
    }

    private void startRecordingService() {
        Intent serviceIntent = new Intent(this, CallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "تم بدء خدمة مراقبة وتسجيل المكالمات بنجاح!", Toast.LENGTH_LONG).show();
        updateStatusText();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                checkOverlayAndBatterySettings();
            } else {
                Toast.makeText(this, "يرجى منح صلاحية الظهور فوق التطبيقات لعرض فقاعة الاختصار العائم", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                }
            }
            if (allGranted) {
                checkOverlayAndBatterySettings();
            } else {
                Toast.makeText(this, "يجب منح كامل الصلاحيات المطلوبة لبدء العمل والخدمة في الخلفية", Toast.LENGTH_LONG).show();
            }
        }
    }
}