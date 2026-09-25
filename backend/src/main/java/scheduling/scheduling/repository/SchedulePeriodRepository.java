package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.SchedulePeriod;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    boolean existsByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);

    List<SchedulePeriod> findAllByStartDateAndEndDateAndVersionNo(LocalDate startDate, LocalDate endDate, Integer versionNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sp
            from SchedulePeriod sp
            where sp.startDate = :startDate
              and sp.endDate = :endDate
              and sp.versionNo = :versionNo
            """)
    List<SchedulePeriod> findAllByStartDateAndEndDateAndVersionNoForUpdate(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("versionNo") Integer versionNo);

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
              and sp.endDate >= coalesce(:from, sp.endDate)
              and sp.startDate <= coalesce(:to, sp.startDate)
            order by sp.startDate desc, sp.id desc
            """)
    List<SchedulePeriod> searchForAdmin(
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
