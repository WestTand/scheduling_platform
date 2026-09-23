package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.SwapRequest;

public interface SwapRequestRepository extends JpaRepository<SwapRequest, Long> {

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where sr.id = :id
            """)
    Optional<SwapRequest> findDetailById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where sr.id = :id
            """)
    Optional<SwapRequest> findDetailByIdForUpdate(@Param("id") Long id);

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where (ra.employee.id = :employeeId or te.id = :employeeId)
              and (:status is null or sr.status = :status)
            order by sr.id desc
            """)
    List<SwapRequest> findRelatedForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status);

    @Query("""
            select sr
            from SwapRequest sr
            join fetch sr.requesterAssignment ra
            join fetch ra.workShift ws
            join fetch ws.schedulePeriod sp
            join fetch ra.employee re
            join fetch sr.targetEmployee te
            left join fetch sr.targetAssignment ta
            left join fetch sr.reviewedBy rb
            where (:status is null or sr.status = :status)
            order by sr.id desc
            """)
    List<SwapRequest> findAllDetail(@Param("status") String status);

    long countByStatus(String status);

    // Phase 9J: employee pending scope = PENDING_TARGET + PENDING_APPROVAL (requester or target side).
    // Manager queue uses countByStatus(PENDING_APPROVAL) only (actionable items).
    @Query("""
            select count(s)
            from SwapRequest s
            where (s.requesterAssignment.employee.id = :employeeId or s.targetEmployee.id = :employeeId)
              and s.status in ('PENDING_TARGET', 'PENDING_APPROVAL')
            """)
    long countPendingForEmployee(@Param("employeeId") Long employeeId);
}
