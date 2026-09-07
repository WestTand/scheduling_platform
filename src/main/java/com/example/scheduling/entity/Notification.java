package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Entity representing a system notification sent to an account.
 *
 * notification_type values (examples):
 *   SCHEDULE_PUBLISHED, LEAVE_APPROVED, LEAVE_REJECTED,
 *   SWAP_REQUESTED, SWAP_APPROVED, SWAP_REJECTED
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notif_recipient", columnList = "recipient_id"),
           @Index(name = "idx_notif_read", columnList = "recipient_id,is_read,created_at")
       })
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Account recipient;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** E.g. SCHEDULE_PUBLISHED, LEAVE_APPROVED, SWAP_REQUESTED. */
    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType;

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
