package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.SchedulePeriod;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    boolean existsByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);

    Optional<SchedulePeriod> findByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);

    boolean existsByStartDateAndEndDateAndVersionNoAndIdNot(
            LocalDate startDate, LocalDate endDate, Integer versionNo, Long id);

    @Query("""
            select sp
            from SchedulePeriod sp
            left join fetch sp.createdBy createdBy
            left join fetch sp.approvedBy approvedBy
            where sp.id = :id
            """)
    Optional<SchedulePeriod> findDetailById(@Param("id") Long id);

    @Query("""
            select sp
            from SchedulePeriod sp
            left join fetch sp.createdBy createdBy
            left join fetch sp.approvedBy approvedBy
            where (:status is null or sp.status = :status)
              and (:from is null or sp.endDate >= :from)
              and (:to is null or sp.startDate <= :to)
            order by sp.startDate desc, sp.id desc
            """)
    List<SchedulePeriod> searchForAdmin(
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
