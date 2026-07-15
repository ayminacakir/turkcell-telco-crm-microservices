package com.turkcell.notification_service.domain.entity;

import com.turkcell.notification_service.domain.enums.NotificationChannel;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * FR-30: Kullanicinin iletisim tercihlerine (opt-in/opt-out) saygi gosterilir.
 * Her (userId, channel) cifti icin bir kayit — kayit yoksa varsayilan opt-in (izinli) kabul edilir.
 */
@Entity
@Table(name = "notification_preferences", schema = "notification_service",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "channel"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "opted_out", nullable = false)
    private boolean optedOut;

    public NotificationPreference() {}

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }

    public boolean isOptedOut() { return optedOut; }
    public void setOptedOut(boolean optedOut) { this.optedOut = optedOut; }
}
