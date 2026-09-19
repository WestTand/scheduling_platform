package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
