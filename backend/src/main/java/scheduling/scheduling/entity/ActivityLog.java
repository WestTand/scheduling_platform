package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Entity representing an audit log entry for every important action in the system.
 *
 * action values: CREATE / UPDATE / DELETE / APPROVE / PUBLISH
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activity_logs",
       indexes = {
           @Index(name = "idx_activity_account", columnList = "account_id"),
           @Index(name = "idx_activity_entity", columnList = "entity_type,entity_id")
       })
public class ActivityLog extends BaseEntity {

    /** Account that performed the action. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** CREATE / UPDATE / DELETE / APPROVE / PUBLISH */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /** Name of the affected domain object (e.g. "shift_assignments", "schedule_periods"). */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** Primary key of the affected record. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Snapshot of the record BEFORE the change (null for CREATE). */
    @Column(name = "old_value_json", columnDefinition = "JSONB")
    private String oldValueJson;

    /** Snapshot of the record AFTER the change (null for DELETE). */
    @Column(name = "new_value_json", columnDefinition = "JSONB")
    private String newValueJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** IP address of the client that performed the action. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
