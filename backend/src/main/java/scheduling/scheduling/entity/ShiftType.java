package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.util.Set;

/**
 * Entity representing a type of work shift.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "shift_types", uniqueConstraints = {@UniqueConstraint(columnNames = {"shift_name"})})
public class ShiftType extends BaseEntity {

    @Column(name = "shift_name", nullable = false, length = 50, unique = true)
    private String shiftName;

    @Column(name = "start_time", nullable = false)
    private java.time.LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private java.time.LocalTime endTime;

    @Column(name = "duration_hours", nullable = false, precision = 4, scale = 1)
    private java.math.BigDecimal durationHours;

    @Column(name = "is_overnight", nullable = false)
    private Boolean overnight = false;

    @Column(name = "is_heavy", nullable = false)
    private Boolean heavy = false;

    @Column(name = "min_staff", nullable = false)
    private Integer minStaff;

    @Column(name = "max_staff", nullable = false)
    private Integer maxStaff;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "shiftType", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<WorkShift> workShifts;

    @OneToMany(mappedBy = "shiftType", fetch = FetchType.LAZY)
    private Set<Preference> preferences;
}
