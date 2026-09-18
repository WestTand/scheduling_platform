package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ShiftType;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    boolean existsByShiftName(String shiftName);

    boolean existsByShiftNameAndIdNot(String shiftName, Long id);

    Optional<ShiftType> findByShiftCode(String shiftCode);

    boolean existsByShiftCode(String shiftCode);

    boolean existsByShiftCodeAndIdNot(String shiftCode, Long id);

    @Query("""
            select s
            from ShiftType s
            where (:query is null or lower(s.shiftName) like lower(concat('%', :query, '%')))
              and (:status is null or s.status = :status)
            order by s.shiftName asc
            """)
    List<ShiftType> search(@Param("query") String query, @Param("status") String status);
}
