package scheduling.scheduling.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);

    // used by Task4, do not remove
    boolean existsByHolidayDateAndActiveTrue(LocalDate holidayDate);
}
