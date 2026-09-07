package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a single run of the CP-SAT scheduling algorithm.
 * Stores input weights, output penalty details, and execution status.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "run_id"))
@Table(name = "optimization_runs",
       indexes = {
           @Index(name = "idx_opt_runs_period", columnList = "period_id"),
           @Index(name = "idx_opt_runs_status", columnList = "status")
       })
public class OptimizationRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private SchedulePeriod schedulePeriod;

    /** The rule set applied in this run (nullable — can reference a specific rule). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private SchedulingRule schedulingRule;

    /** Account that triggered this run. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by")
    private Account startedBy;

    /** RUNNING / SUCCESS / FAILED / INFEASIBLE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Soft-constraint weights used in objective function (w1..w5). */
    @Column(name = "weights_json", columnDefinition = "JSONB")
    private String weightsJson;

    /** Per-criterion penalty breakdown from the solver. */
    @Column(name = "penalty_details_json", columnDefinition = "JSONB")
    private String penaltyDetailsJson;

    @Column(name = "total_penalty", precision = 10, scale = 2)
    private BigDecimal totalPenalty;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Reason the solver returned INFEASIBLE (e.g., not enough staff, quota mismatch). */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
