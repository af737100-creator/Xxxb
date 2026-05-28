package com.recorder.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class CallService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // إنشاء إشعار دائم لجعل الخدمة لا تموت أبداً
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "RecordingChannel",
                "خدمة التسجيل النشطة",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        Notification notification = new NotificationCompat.Builder(this, "RecordingChannel")
            .setContentTitle("مسجل المكالمات نشط بالخلفية")
            .setContentText("التطبيق يعمل في الخلفية لمراقبة وتسجيل المكالمات.")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
            
        startForeground(1, notification);
        return START_STICKY; // هذا الأمر يجعل الخدمة تعيد تشغيل نفسها لو أغلقها النظام
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}