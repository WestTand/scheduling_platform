package scheduling.scheduling.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.Preference;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    @Query("""
            select p from Preference p
            left join fetch p.shiftType
            where p.employee.id = :employeeId
            and (:status is null or p.status = :status)
            and p.preferenceDate >= coalesce(:from, p.preferenceDate)
            and p.preferenceDate <= coalesce(:to, p.preferenceDate)
            order by p.preferenceDate asc, p.id desc
            """)
    List<Preference> searchForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(p) > 0 from Preference p
            where p.employee.id = :employeeId
            and p.preferenceDate = :preferenceDate
            and coalesce(p.shiftType.id, -1) = coalesce(:shiftTypeId, -1)
            and p.preferenceType = :preferenceType
            and p.status in ('PENDING', 'APPROVED')
            and p.id <> coalesce(:excludedPreferenceId, p.id)
            """)
    boolean existsActiveDuplicate(
            @Param("employeeId") Long employeeId,
            @Param("preferenceDate") LocalDate preferenceDate,
            @Param("shiftTypeId") Long shiftTypeId,
            @Param("preferenceType") String preferenceType,
            @Param("excludedPreferenceId") Long excludedPreferenceId);

    Optional<Preference> findByIdAndEmployeeId(Long id, Long employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Preference p
            where p.id = :id
            and p.employee.id = :employeeId
            """)
    Optional<Preference> findByIdAndEmployeeIdForUpdate(
            @Param("id") Long id,
            @Param("employeeId") Long employeeId);

    @Query("""
            select p from Preference p
            join fetch p.employee e
            left join fetch p.shiftType st
            where (:status is null or p.status = :status)
            and p.preferenceDate >= coalesce(:from, p.preferenceDate)
            and p.preferenceDate <= coalesce(:to, p.preferenceDate)
            order by p.preferenceDate asc, p.id desc
            """)
    List<Preference> searchForAdmin(
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Preference p
            join fetch p.employee e
            left join fetch p.shiftType st
            where p.id = :id
            """)
    Optional<Preference> findByIdForAdminReview(@Param("id") Long id);

    @Query("""
            select p from Preference p
            join fetch p.employee e
            left join fetch p.shiftType st
            where p.preferenceDate between :periodStart and :periodEnd
              and p.status in ('PENDING', 'APPROVED')
            order by p.preferenceDate asc, p.id asc
            """)
    List<Preference> findActiveInRange(
            @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    long countByStatus(String status);

    long countByEmployeeIdAndStatus(Long employeeId, String status);

    boolean existsByEmployeeIdAndPreferenceDateAndPreferenceTypeAndStatus(
            Long employeeId, LocalDate preferenceDate, String preferenceType, String status);
}
