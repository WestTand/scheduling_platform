package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Entity representing a specific work shift (ca làm việc).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "work_shift_id"))
@Table(name = "work_shifts",
       indexes = {@Index(name = "idx_work_shifts_date_type", columnList = "work_date,shift_type_id")})
public class WorkShift extends BaseEntity {

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "required_staff", nullable = false)
    private Integer requiredStaff;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private SchedulePeriod schedulePeriod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_type_id", nullable = false)
    private ShiftType shiftType;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "work_minutes")
    private Integer workMinutes;

    @Column(name = "is_transition_shift")
    private Boolean transitionShift;

    @OneToMany(mappedBy = "workShift", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private Set<ShiftAssignment> shiftAssignments;
}
