package edu.msu.cse476.haidaris.finance_tracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Handles creating and sending the overspending warning notification.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID   = "overspending_warnings";
    private static final String CHANNEL_NAME = "Overspending Warnings";
    private static final int    NOTIF_ID     = 1001;

    /**
     * Creates the notification channel.
     * Must be called before sending any notification.
     */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Warns you when near a store while at overspending risk");
            channel.enableVibration(true);

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Sends the overspending warning notification.
     *
     * @param context     Android context
     * @param storeName   Name of the nearby store
     * @param riskPercent Risk percentage from the ML model (0-100%)
     */
    public static void sendWarning(Context context, String storeName, double riskPercent) {
        // Tapping the notification opens the dashboard
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Overspending Warning";
        String body  = String.format(
                "You're near %s with %.0f%% overspending risk. Consider your budget before shopping.",
                storeName, riskPercent
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500});

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}