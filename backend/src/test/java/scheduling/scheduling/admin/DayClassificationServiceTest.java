package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.repository.HolidayRepository;

class DayClassificationServiceTest {

    private final HolidayRepository holidayRepository =
            org.mockito.Mockito.mock(HolidayRepository.class);
    private final DayClassificationService service = new DayClassificationService(holidayRepository);

    @Test
    void holidayTakesPrecedenceOverWeekend() {
        LocalDate saturdayHoliday = LocalDate.of(2026, 9, 19);
        when(holidayRepository.existsByHolidayDateAndActiveTrue(saturdayHoliday)).thenReturn(true);

        assertThat(service.classify(saturdayHoliday))
                .isEqualTo(DayClassificationService.DayType.HOLIDAY);
    }

    @Test
    void saturdayWithoutHolidayIsWeekend() {
        LocalDate saturday = LocalDate.of(2026, 9, 19);
        when(holidayRepository.existsByHolidayDateAndActiveTrue(saturday)).thenReturn(false);

        assertThat(service.classify(saturday))
                .isEqualTo(DayClassificationService.DayType.WEEKEND);
    }

    @Test
    void mondayWithoutHolidayIsWeekday() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        when(holidayRepository.existsByHolidayDateAndActiveTrue(monday)).thenReturn(false);

        assertThat(service.classify(monday))
                .isEqualTo(DayClassificationService.DayType.WEEKDAY);
    }

    @Test
    void inactiveHolidayFallsBackToWeekdayOrWeekend() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        when(holidayRepository.existsByHolidayDateAndActiveTrue(monday)).thenReturn(false);

        assertThat(service.classify(monday))
                .isEqualTo(DayClassificationService.DayType.WEEKDAY);
    }
}
