package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity representing user accounts. Role is stored as ENUM.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "account_id"))
@Table(name = "accounts", uniqueConstraints = {@UniqueConstraint(columnNames = {"username"})})
public class Account extends BaseEntity {

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Optional one-to-one link to Employee (manager accounts have null employee)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    // mappedBy must match field name "recipient" in Notification
    @OneToMany(mappedBy = "recipient", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<Notification> notifications;

    // mappedBy must match field name "account" in ActivityLog
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<ActivityLog> activityLogs;
}
