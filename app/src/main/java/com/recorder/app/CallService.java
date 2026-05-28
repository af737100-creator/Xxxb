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
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // الرد على مكالمة أو الاتصال (كل من الواردة والصادرة) -> بدء التسجيل التلقائي فوراً
                    if (floatingView == null) {
                        showFloatingBubble();
                    }
                    startAudioRecording();
                    updateFloatingBubbleState(true);
                } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                    // رنين المكالمة (الواردة) -> إظهار الزر تهيئة للتسجيل
                    if (floatingView == null) {
                        showFloatingBubble();
                    }
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    // انتهاء المكالمة -> إيقاف وحفظ التسجيل تلقائياً وإخفاء الزر العائم
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
        bubbleButton.setTextColor(0xFFFFFFFF);
        updateBubbleAppearance(bubbleButton, isRecording);

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
                updateBubbleAppearance(bubbleButton, true);
                Toast.makeText(this, "تم بدء تسجيل المكالمة تلقائياً!", Toast.LENGTH_SHORT).show();
            }
        } else {
            stopAudioRecording();
            updateBubbleAppearance(bubbleButton, false);
            Toast.makeText(this, "تم حفظ تسجيل المكالمة في مجلد مخصص بنجاح!", Toast.LENGTH_LONG).show();
        }
    }

    private void updateFloatingBubbleState(boolean recording) {
        if (floatingView instanceof Button) {
            updateBubbleAppearance((Button) floatingView, recording);
        }
    }

    private void updateBubbleAppearance(Button bubbleButton, boolean recording) {
        if (recording) {
            bubbleButton.setText("🛑");
            bubbleButton.setBackgroundColor(0xFFE53935); // أحمر للتسجيل النشط
        } else {
            bubbleButton.setText("🎙️");
            bubbleButton.setBackgroundColor(0xFFFF5722); // برتقالي مميز
        }
    }

    private void startAudioRecording() {
        if (isRecording) return;

        File sampleDir = new File(getExternalFilesDir(null), "MyRecordings");
        if (!sampleDir.exists()) {
            sampleDir.mkdirs();
        }

        // اسم ملف واضح بالوقت والملحق mp4
        audioFile = new File(sampleDir, "Call_Record_" + System.currentTimeMillis() + ".mp4");

        // تجربة بذكاء قنوات متعددة لتسجيل صوت الطرفين:
        // 1. VOICE_COMMUNICATION (قناة مشفرة ذكية مخصصة لل VoIP والمكالمات الطرفية)
        // 2. VOICE_RECOGNITION (التعرف الذكي عالي الصوت)
        // 3. MIC (الميكروفون الداخلي لترشيح الصوت)
        int[] audioSources = {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        };

        boolean success = false;
        String lastError = "";

        for (int source : audioSources) {
            try {
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(source);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(64000); // 64kbps دقة عالية
                mediaRecorder.setAudioSamplingRate(16000);   // سحب مريح
                mediaRecorder.setOutputFile(audioFile.getAbsolutePath());

                mediaRecorder.prepare();
                mediaRecorder.start();
                success = true;
                break;
            } catch (Exception e) {
                lastError = e.getMessage();
                if (mediaRecorder != null) {
                    try {
                        mediaRecorder.release();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    mediaRecorder = null;
                }
            }
        }

        if (success) {
            isRecording = true;
        } else {
            isRecording = false;
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
            Toast.makeText(this, "فشل تهيئة مسجل المكالمات ثنائي الطرفين: " + lastError, Toast.LENGTH_LONG).show();
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