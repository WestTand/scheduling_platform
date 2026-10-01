package scheduling.scheduling.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ShiftAssignment;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sa.id = :id
            """)
    Optional<ShiftAssignment> findDetailById(@Param("id") Long id);

    long countByWorkShiftId(Long workShiftId);

    boolean existsByWorkShiftIdAndEmployeeId(Long workShiftId, Long employeeId);

    // HQL has no `of` clause: Hibernate derives the `for no key update of <alias>` list from the
    // pessimistic lock scope, whose default (ROOT_ONLY) locks the root table only. Keep the fetch
    // joins the service reads but drop shiftType: approving a swap must row-lock only
    // shift_assignments, so concurrent writes to work_shifts / employees are not blocked.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch sa.employee e
            where sa.id in :ids
            order by sa.id asc
            """)
    List<ShiftAssignment> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    Optional<ShiftAssignment> findByWorkShiftIdAndEmployeeId(Long workShiftId, Long employeeId);

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sa.employee.id = :employeeId
              and ws.workDate between :from and :to
            order by ws.workDate asc, st.startTime asc, sa.id asc
            """)
    List<ShiftAssignment> findForEmployeeInWindow(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sp.id = :periodId
            order by ws.workDate asc, st.startTime asc, sa.id asc
            """)
    List<ShiftAssignment> listForPeriod(@Param("periodId") Long periodId);

    // Fixed = locked OR manual: 'MANUAL' literal mirrors ShiftAssignment.assignedSource contract (AUTO/MANUAL).
    @Query("""
            select sa from ShiftAssignment sa
            join fetch sa.employee e
            join fetch sa.workShift ws
            join fetch ws.shiftType st
            where ws.schedulePeriod.id = :periodId
              and (sa.locked = true or sa.assignedSource = 'MANUAL')
            order by sa.id asc
            """)
    List<ShiftAssignment> findFixedForPeriod(@Param("periodId") Long periodId);

    @Query("""
            select sa
            from ShiftAssignment sa
            join fetch sa.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            join fetch sa.employee e
            where sp.id <> :periodId
              and ws.workDate between :from and :to
            order by ws.workDate asc, st.startTime asc, sa.id asc
            """)
    List<ShiftAssignment> findBoundaryForAiPayload(
            @Param("periodId") Long periodId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
