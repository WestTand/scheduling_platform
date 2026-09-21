package scheduling.scheduling.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.ShiftType;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    boolean existsByShiftName(String shiftName);

    boolean existsByShiftNameAndIdNot(String shiftName, Long id);

    Optional<ShiftType> findByShiftCode(String shiftCode);

    Optional<ShiftType> findByShiftName(String shiftName);

    boolean existsByShiftCode(String shiftCode);

    boolean existsByShiftCodeAndIdNot(String shiftCode, Long id);

    default List<ShiftType> search(String query, String status) {
        if (query == null && status == null) {
            return findAllByOrderByShiftNameAsc();
        }
        if (query == null) {
            return findByStatusOrderByShiftNameAsc(status);
        }
        if (status == null) {
            return findByShiftNameContainingIgnoreCaseOrderByShiftNameAsc(query);
        }
        return findByShiftNameContainingIgnoreCaseAndStatusOrderByShiftNameAsc(query, status);
    }

    List<ShiftType> findAllByOrderByShiftNameAsc();

    List<ShiftType> findByStatusOrderByShiftNameAsc(String status);

    List<ShiftType> findByShiftNameContainingIgnoreCaseOrderByShiftNameAsc(String shiftName);

    List<ShiftType> findByShiftNameContainingIgnoreCaseAndStatusOrderByShiftNameAsc(String shiftName, String status);

    List<ShiftType> findByStatus(String status);
}
