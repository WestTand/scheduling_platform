package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;

/**
 * Entity representing an assignment of an employee to a work shift.
 * Unique constraint (work_shift_id, employee_id) prevents duplicate assignments.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "shift_assignments",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"work_shift_id", "employee_id"})})
public class ShiftAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_shift_id", nullable = false)
    private WorkShift workShift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** ASSIGNED / CONFIRMED / SWAPPED */
    @Column(name = "assignment_status", nullable = false, length = 20)
    private String assignmentStatus = "ASSIGNED";

    /** AUTO (algorithm) / MANUAL (manager override) */
    @Column(name = "assigned_source", nullable = false, length = 20)
    private String assignedSource = "AUTO";

    /** If true, algorithm cannot overwrite this assignment when re-running. */
    @Column(name = "is_locked", nullable = false)
    private Boolean locked = false;
    // created_at is inherited from BaseEntity
}

