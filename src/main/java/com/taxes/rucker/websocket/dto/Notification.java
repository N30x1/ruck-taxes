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

    @SerializedName("from_player_id")
    private String fromPlayerId;

    @SerializedName("from_rsn")
    private String fromPlayerRsn;

    // MODIFIED: This is now the anonymous ID of the recipient.
    private String toPlayerId;

    private Instant timestamp;
    private NotificationType type;
    private NotificationStatus status;

    public Notification(String notificationId, String orderId, String message, String fromPlayerId, String fromPlayerRsn, String toPlayerId, Instant timestamp, NotificationType type, NotificationStatus status) {
        this.notificationId = notificationId;
        this.orderId = orderId;
        this.message = message;
        this.fromPlayerId = fromPlayerId;
        this.fromPlayerRsn = fromPlayerRsn;
        this.toPlayerId = toPlayerId;
        this.timestamp = timestamp;
        this.type = type;
        this.status = status;
    }

    public Notification() {
        this.timestamp = Instant.now();
    }
}