package scheduling.scheduling.admin;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import scheduling.scheduling.repository.HolidayRepository;

@Service
public class DayClassificationService {

    public enum DayType {
        HOLIDAY,
        WEEKEND,
        WEEKDAY
    }

    private final HolidayRepository holidayRepository;

    public DayClassificationService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public DayType classify(LocalDate date) {
        if (holidayRepository.existsByHolidayDateAndActiveTrue(date)) {
            return DayType.HOLIDAY;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.WEEKEND;
        }
        return DayType.WEEKDAY;
    }
}
