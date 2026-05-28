package com.recorder.app;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaRecorder;
import android.os.*;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
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
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
                    if (floatingView == null) {
                        showFloatingBubble();
                    }
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    stopAudioRecording();
                    removeFloatingBubble();
                }
            }
        };
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupForegroundNotification();
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
            .setContentTitle("مسجل المكالمات في وضع الاستعداد")
            .setContentText("التطبيق يراقب حالة الهاتف لتنشيط الزر العائم وتجهيز المكالمة تلقائياً.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
            
        startForeground(1, notification);
    }

    private void showFloatingBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

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

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 400;

        bubbleButton.setOnTouchListener(new View.OnTouchListener() {
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
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleButton, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        // إذا كانت المسافة التي تحركها الإصبع أقل من 10 بكسل، نعتبرها نقرة (Click) بدلاً من سحب (Drag)
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            toggleCallRecording(bubbleButton);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(bubbleButton, params);
        floatingView = bubbleButton;
    }

    private void removeFloatingBubble() {
        if (windowManager != null && floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatingView = null;
        }
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
            Toast.makeText(this, "تم حفظ تسجيل المكالمة في مجلد مخصص بنجاح!", Toast.LENGTH_LONG).show();
        }
    }

    private void startAudioRecording() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            
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
        removeFloatingBubble();
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}