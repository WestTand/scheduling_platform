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
@AttributeOverride(name = "id", column = @Column(name = "shift_type_id"))
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
    @Builder.Default
    private Boolean overnight = false;

    @Column(name = "is_heavy", nullable = false)
    @Builder.Default
    private Boolean heavy = false;

    @Column(name = "min_staff", nullable = false)
    private Integer minStaff;

    @Column(name = "max_staff", nullable = false)
    private Integer maxStaff;

    @Column(name = "quota_category", nullable = false, length = 20, columnDefinition = "varchar(20) default 'REGULAR'")
    @Builder.Default
    private String quotaCategory = "REGULAR";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "shift_code", length = 30, unique = true)
    private String shiftCode;

    @Column(name = "applicable_weekdays", nullable = false, length = 40)
    @Builder.Default
    private String applicableWeekdays = "MON,TUE,WED,THU,FRI,SAT,SUN";

    @Column(name = "applies_on_holidays", nullable = false)
    @Builder.Default
    private Boolean appliesOnHolidays = false;

    @Column(name = "penalty_weight_multiplier", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private java.math.BigDecimal penaltyWeightMultiplier = java.math.BigDecimal.valueOf(1.00).setScale(2);

    @OneToMany(mappedBy = "shiftType", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<WorkShift> workShifts;

    @OneToMany(mappedBy = "shiftType", fetch = FetchType.LAZY)
    private Set<Preference> preferences;
}
