package com.recorder.app;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaRecorder;
import android.os.*;
import android.view.*;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.IOException;

public class CallService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private File audioFile;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupForegroundNotification();
        showFloatingBubble();
        return START_STICKY; // إعادة التشغيل التلقائي من النظام فور الإغلاق
    }

    private void setupForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "RecordingChannel",
                "خدمة تسجيل المكالمات الخلفية",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        Notification notification = new NotificationCompat.Builder(this, "RecordingChannel")
            .setContentTitle("مسجل المكالمات والزر العائم نشط")
            .setContentText("التطبيق في حالة استعداد لتسجيل المكالمات عبر الزر العائم.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
            
        startForeground(1, notification);
    }

    private void showFloatingBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        // تهيئة واجهة الزر العائم برمجياً وتصميمه بشكل فقاعة مستديرة أنيقة
        final Button bubbleButton = new Button(this);
        bubbleButton.setText("🎙️");
        bubbleButton.setBackgroundColor(0xFFFF5722); // برتقالي مميز
        bubbleButton.setTextColor(0xFFFFFFFF);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        // وضع الفقاعة في منتصف اليمين كشكل افتراضي
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 400;

        // دعم تحريك الزر بسحب اليد (Drag Events)
        bubbleButton.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleButton, params);
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            // تم النقر بضغطة سريعة -> بدء أو إنهاء تسجيل الصوت
                            toggleCallRecording(bubbleButton);
                        }
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(bubbleButton, params);
        floatingView = bubbleButton;
    }

    private void toggleCallRecording(Button bubbleButton) {
        if (!isRecording) {
            startAudioRecording();
            if (isRecording) {
                bubbleButton.setText("🛑");
                bubbleButton.setBackgroundColor(0xFFE53935); // أحمر للتسجيل النشط
                Toast.makeText(this, "جاري تسجيل المكالمة ...", Toast.LENGTH_SHORT).show();
            }
        } else {
            stopAudioRecording();
            bubbleButton.setText("🎙️");
            bubbleButton.setBackgroundColor(0xFFFF5722); // العودة للون العادي
            Toast.makeText(this, "تم حفظ تسجيل المكالمة بنجاح في مجلد التسجيلات!", Toast.LENGTH_LONG).show();
        }
    }

    private void startAudioRecording() {
        try {
            mediaRecorder = new MediaRecorder();
            
            // شرح متوافق مع نظام أندرويد لحماية خصوصية المكالمات:
            // في أنظمة أندرويد الحديثة (9 و 10 وما فوق)، يمنع النظام تماماً استخدام مصادر صوت الخطوط الداخلية المباشرة لغير مكالمات النظام الأصلية.
            // الطريقة المعتمدة والصحيحة لجميع الأجهزة والاصدارات مع تسجيل واضح لكلا الطرفين بدون Speaker-Mode
            // هي استخدام مصدر الميكروفون القياسي والتقاط صوت السماعة الخارجية مباشرة بعد رفع مستوى الصوت.
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            
            // توجيه المسار للمجلد التابع لحفظ التسجيلات والاستماع إليها لاحقاً
            File sampleDir = new File(getExternalFilesDir(null), "MyRecordings");
            if (!sampleDir.exists()) {
                sampleDir.mkdirs();
            }
            audioFile = File.createTempFile("Recording_" + System.currentTimeMillis() + "_", ".mp4", sampleDir);
            
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تهيئة مسجل الصوت: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void stopAudioRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAudioRecording();
        if (windowManager != null && floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}