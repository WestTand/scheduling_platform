package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entity representing a preference (nguyện vọng) of an employee for a shift type on a given date.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "preference_id"))
@Table(name = "preferences",
       indexes = {
           @Index(name = "idx_pref_employee", columnList = "employee_id"),
           @Index(name = "idx_pref_shift_type", columnList = "shift_type_id"),
           @Index(name = "idx_pref_date", columnList = "preference_date")
       })
public class Preference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_type_id")
    private ShiftType shiftType; // optional

    @Column(name = "preference_date", nullable = false)
    private LocalDate preferenceDate;

    @Column(name = "preference_type", nullable = false, length = 20)
    @Builder.Default
    private String preferenceType = "WANT"; // WANT / AVOID, could be enum

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // could be enum
}
