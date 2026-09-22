package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.LeaveRequest;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query("""
            select l from LeaveRequest l
            where l.employee.id = :employeeId
            and (:status is null or l.status = :status)
            and (:from is null or l.endDate >= :from)
            and (:to is null or l.startDate <= :to)
            order by l.id desc
            """)
    List<LeaveRequest> searchForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
            and l.status in ('PENDING', 'APPROVED')
            and l.startDate <= :endDate
            and l.endDate >= :startDate
            """)
    boolean existsOverlappingActiveRequest(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Optional<LeaveRequest> findByIdAndEmployeeId(Long id, Long employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from LeaveRequest l
            where l.id = :id
            and l.employee.id = :employeeId
            """)
    Optional<LeaveRequest> findByIdAndEmployeeIdForUpdate(
            @Param("id") Long id,
            @Param("employeeId") Long employeeId);

    @Query("""
            select l from LeaveRequest l
            join fetch l.employee e
            left join fetch l.reviewedBy r
            where (:employeeId is null or e.id = :employeeId)
            and (:status is null or l.status = :status)
            and (:from is null or l.endDate >= :from)
            and (:to is null or l.startDate <= :to)
            order by l.id desc
            """)
    List<LeaveRequest> searchForAdmin(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from LeaveRequest l
            where l.id = :id
            """)
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
            and l.status = 'APPROVED'
            and l.startDate <= :endDate
            and l.endDate >= :startDate
            and l.id <> :excludedLeaveRequestId
            """)
    boolean existsApprovedOverlapExcludingId(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedLeaveRequestId") Long excludedLeaveRequestId);

    @Query("""
            select lr from LeaveRequest lr
            join fetch lr.employee e
            where lr.status = 'APPROVED'
              and lr.startDate <= :periodEnd
              and lr.endDate >= :periodStart
            order by lr.startDate asc, lr.id asc
            """)
    List<LeaveRequest> findApprovedOverlapping(
            @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    long countByStatus(String status);

    long countByEmployeeIdAndStatus(Long employeeId, String status);
}
