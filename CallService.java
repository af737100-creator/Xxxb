package com.recorder.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class CallService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // إنشاء إشعار دائم لجعل الخدمة لا تموت أبداً
        NotificationChannel channel = new NotificationChannel("1", "Rec", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        
        Notification notification = new NotificationCompat.Builder(this, "1")
            .setContentTitle("التسجيل نشط")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .build();
            
        startForeground(1, notification);
        return START_STICKY; // هذا الأمر يجعل الخدمة تعيد تشغيل نفسها لو أغلقها النظام
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
