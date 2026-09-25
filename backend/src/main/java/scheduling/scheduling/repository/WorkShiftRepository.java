package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    Optional<WorkShift> findBySchedulePeriodIdAndWorkDateAndShiftTypeId(Long periodId, LocalDate workDate, Long shiftTypeId);

    @Query("""
            select ws
            from WorkShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            where sp.id = :periodId
              and ws.workDate = coalesce(:date, ws.workDate)
              and st.id = coalesce(:shiftTypeId, st.id)
            order by ws.workDate asc, st.startTime asc, ws.id asc
            """)
    List<WorkShift> searchForPeriod(
            @Param("periodId") Long periodId,
            @Param("date") LocalDate date,
            @Param("shiftTypeId") Long shiftTypeId);

    @Query("""
            select ws
            from WorkShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            where ws.id = :id
            """)
    Optional<WorkShift> findDetailById(@Param("id") Long id);

    @Query("""
            select ws from WorkShift ws
            join fetch ws.shiftType
            where ws.schedulePeriod.id = :periodId
            order by ws.workDate asc, ws.id asc
            """)
    List<WorkShift> findForAiPayload(@Param("periodId") Long periodId);

    @Query("""
            select distinct ws
            from WorkShift ws
            join fetch ws.schedulePeriod sp
            join fetch ws.shiftType st
            left join fetch ws.shiftAssignments sa
            left join fetch sa.employee e
            where sp.id = :periodId
            order by ws.workDate asc, st.startTime asc, ws.id asc, e.employeeCode asc, e.fullName asc, sa.id asc
            """)
    List<WorkShift> findExportDetailForPeriod(@Param("periodId") Long periodId);
}
