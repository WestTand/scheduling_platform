package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Set;

/**
 * Entity representing a scheduling period (kỳ lập lịch).
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "period_id"))
@Table(name = "schedule_periods", uniqueConstraints = {@UniqueConstraint(columnNames = {"start_date", "end_date", "version_no"})})
public class SchedulePeriod extends BaseEntity {

    @Column(name = "period_name", nullable = false, length = 100)
    private String periodName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private Integer versionNo = 1;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Account createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Account approvedBy;

    @Column(name = "published_at")
    private java.time.LocalDateTime publishedAt;

    @Column(name = "total_penalty", precision = 10, scale = 2)
    private java.math.BigDecimal totalPenalty;

    @OneToMany(mappedBy = "schedulePeriod", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<WorkShift> workShifts;

    @OneToMany(mappedBy = "schedulePeriod", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<OptimizationRun> optimizationRuns;
}
