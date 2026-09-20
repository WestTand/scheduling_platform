package scheduling.scheduling.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query("""
            select al
            from ActivityLog al
            join fetch al.account a
            where (:action is null or al.action = :action)
              and (:entityType is null or al.entityType = :entityType)
              and (:accountId is null or a.id = :accountId)
              and (:from is null or al.createdAt >= :from)
              and (:toExclusive is null or al.createdAt < :toExclusive)
            order by al.createdAt desc, al.id desc
            """)
    List<ActivityLog> search(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("accountId") Long accountId,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);
}
