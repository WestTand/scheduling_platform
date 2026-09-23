package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity representing an employee.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "employee_id"))
@Table(name = "employees", uniqueConstraints = {@UniqueConstraint(columnNames = {"employee_code"})})
public class Employee extends BaseEntity {

    @Column(name = "employee_code", nullable = false, length = 20, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // bi‑directional one‑to‑many to ShiftAssignment
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<ShiftAssignment> shiftAssignments;

    // bi‑directional one‑to‑many to LeaveRequest
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<LeaveRequest> leaveRequests;

    // bi‑directional one‑to‑many to Preference
    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<Preference> preferences;

    // optional one‑to‑one back to Account (manager account)
    @OneToOne(mappedBy = "employee", fetch = FetchType.LAZY)
    private Account account;
}
