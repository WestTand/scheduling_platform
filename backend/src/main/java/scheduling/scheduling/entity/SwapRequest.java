package scheduling.scheduling.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a shift-swap request between two employees.
 *
 * <ul>
 *   <li>requesterAssignment: the shift the requester currently holds (non-null).</li>
 *   <li>targetEmployee: the person being asked to take/swap the shift (non-null).</li>
 *   <li>targetAssignment: the target's shift offered in return; NULL means "please cover my shift only".</li>
 * </ul>
 *
 * Status flow:
 *   PENDING_TARGET → PENDING_APPROVAL → APPROVED / REJECTED
 *   PENDING_TARGET → CANCELLED (requester cancels before target responds)
 *   PENDING_TARGET / PENDING_APPROVAL → REJECTED (target declines or manager rejects)
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "swap_request_id"))
@Table(name = "swap_requests",
       indexes = {
           @Index(name = "idx_swap_requester", columnList = "requester_assignment_id"),
           @Index(name = "idx_swap_target_emp", columnList = "target_employee_id")
       })
public class SwapRequest extends BaseEntity {

    /** Ca của người gửi yêu cầu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_assignment_id", nullable = false)
    private ShiftAssignment requesterAssignment;

    /** Người được đề nghị nhận / đổi ca. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_employee_id", nullable = false)
    private Employee targetEmployee;

    /** Ca của người nhận (null nếu chỉ nhờ đỡ, không đổi ngược lại). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_assignment_id")
    private ShiftAssignment targetAssignment;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /**
     * PENDING_TARGET   — chờ người nhận xác nhận
     * PENDING_APPROVAL — chờ quản lý duyệt
     * APPROVED         — đã duyệt
     * REJECTED         — từ chối
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING_TARGET";

    /** Quản lý duyệt yêu cầu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Account reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
