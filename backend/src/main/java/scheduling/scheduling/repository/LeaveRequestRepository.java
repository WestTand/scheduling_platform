package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
