package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Entity representing a specific work shift (ca làm việc).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
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

    @OneToMany(mappedBy = "workShift", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<ShiftAssignment> shiftAssignments;
}
