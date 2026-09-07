package com.example.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entity representing a leave request.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "leave_requests",
       indexes = {@Index(name = "idx_leave_employee", columnList = "employee_id,start_date,end_date")})
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // could be enum

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Account reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;
}
