package com.taxes.rucker.websocket.dto;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Notification {
    public enum NotificationType { SENT, RECEIVED }
    public enum NotificationStatus { PENDING, ACCEPTED, COMPLETED, CANCELLED }

    @SerializedName("notification_id")
    private String notificationId;

    @SerializedName("order_id")
    private String orderId;

    private String message;

    @SerializedName("from_rsn")
    private String fromPlayer;

    @SerializedName("to_rsn")
    private String toPlayer;

    private Instant timestamp;
    private NotificationType type;
    private NotificationStatus status;

    public Notification(String notificationId, String orderId, String message, String fromPlayer, String toPlayer, Instant timestamp, NotificationType type, NotificationStatus status) {
        this.notificationId = notificationId;
        this.orderId = orderId;
        this.message = message;
        this.fromPlayer = fromPlayer;
        this.toPlayer = toPlayer;
        this.timestamp = timestamp;
        this.type = type;
        this.status = status;
    }

    public Notification() {
        this.timestamp = Instant.now();
    }
}