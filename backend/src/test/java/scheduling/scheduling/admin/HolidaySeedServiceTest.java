package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import scheduling.scheduling.entity.Holiday;
import scheduling.scheduling.repository.HolidayRepository;

class HolidaySeedServiceTest {

    private HolidayRepository holidayRepository;
    private ResourcePatternResolver resourceResolver;
    private HolidaySeedService seedService;

    @BeforeEach
    void setUp() {
        holidayRepository = mock(HolidayRepository.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        seedService = new HolidaySeedService(holidayRepository, resourceResolver);
        when(holidayRepository.save(any(Holiday.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Resource jsonResource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fixedHolidaysGeneratedForRequestedYear() {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());

        seedService.seedFixedHolidaysForYear(2026);

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository, times(4)).save(captor.capture());
        List<LocalDate> dates = captor.getAllValues().stream().map(Holiday::getHolidayDate).toList();
        assertThat(dates).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 9, 2));
        for (Holiday saved : captor.getAllValues()) {
            assertThat(saved.getHolidayYear()).isEqualTo(2026);
            assertThat(saved.getActive()).isTrue();
            assertThat(saved.getSourceType()).isEqualTo("FIXED_RULE");
            assertThat(saved.getHolidayType()).isEqualTo("PUBLIC_HOLIDAY");
            assertThat(saved.getHolidayName()).isNotBlank();
        }
    }

    @Test
    void jsonHolidays2026ImportsAnnualHolidays() {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());
        String json = """
                [
                  {"date": "2026-02-16", "name": "Nghi Tet Nguyen dan", "type": "LUNAR_HOLIDAY", "sourceReference": "Thong bao lich nghi nam 2026"},
                  {"date": "2026-04-26", "name": "Gio To Hung Vuong", "type": "LUNAR_HOLIDAY", "sourceReference": "Thong bao lich nghi nam 2026"}
                ]
                """;

        seedService.importAnnualHolidayResource("holidays-2026.json", jsonResource(json));

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository, times(2)).save(captor.capture());
        List<LocalDate> dates = captor.getAllValues().stream().map(Holiday::getHolidayDate).toList();
        assertThat(dates).containsExactlyInAnyOrder(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 4, 26));
        for (Holiday saved : captor.getAllValues()) {
            assertThat(saved.getHolidayYear()).isEqualTo(2026);
            assertThat(saved.getSourceType()).isEqualTo("ANNUAL_SEED");
            assertThat(saved.getActive()).isTrue();
        }
    }

    @Test
    void duplicateDateUpsertsExistingRow() {
        Holiday existing = Holiday.builder()
                .holidayDate(LocalDate.of(2026, 9, 2))
                .holidayName("Old name")
                .holidayYear(2026)
                .holidayType("PUBLIC_HOLIDAY")
                .sourceType("FIXED_RULE")
                .active(true)
                .build();
        when(holidayRepository.findByHolidayDate(LocalDate.of(2026, 9, 2)))
                .thenReturn(Optional.of(existing));
        String json = """
                [
                  {"date": "2026-09-02", "name": "Quoc khanh (updated)", "type": "PUBLIC_HOLIDAY", "sourceReference": "Thong bao 2026"}
                ]
                """;

        seedService.importAnnualHolidayResource("holidays-2026.json", jsonResource(json));

        verify(holidayRepository, times(1)).save(existing);
        assertThat(existing.getHolidayName()).isEqualTo("Quoc khanh (updated)");
        assertThat(existing.getSourceType()).isEqualTo("ANNUAL_SEED");
        assertThat(existing.getActive()).isTrue();
    }

    @Test
    void invalidFilenameYearRejects() {
        assertThatThrownBy(() -> seedService.importAnnualHolidayResource("holidays-20XX.json",
                jsonResource("[]")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidDateInJsonRejects() {
        String json = """
                [
                  {"date": "not-a-date", "name": "Bad", "type": "PUBLIC_HOLIDAY"}
                ]
                """;
        assertThatThrownBy(() -> seedService.importAnnualHolidayResource("holidays-2026.json",
                jsonResource(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void yearMismatchRejects() {
        String json = """
                [
                  {"date": "2027-01-01", "name": "Wrong year", "type": "PUBLIC_HOLIDAY"}
                ]
                """;
        assertThatThrownBy(() -> seedService.importAnnualHolidayResource("holidays-2026.json",
                jsonResource(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownTypeRejects() {
        String json = """
                [
                  {"date": "2026-01-02", "name": "Weird", "type": "MOON_DAY"}
                ]
                """;
        assertThatThrownBy(() -> seedService.importAnnualHolidayResource("holidays-2026.json",
                jsonResource(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void repeatedSeedDoesNotCreateDuplicates() {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());

        seedService.seedFixedHolidaysForYear(2026);
        verify(holidayRepository, times(4)).save(any(Holiday.class));

        // Second run finds existing rows and updates them instead of creating new ones.
        when(holidayRepository.findByHolidayDate(any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(0);
                    Holiday h = Holiday.builder()
                            .holidayDate(date)
                            .holidayName("existing")
                            .holidayYear(2026)
                            .holidayType("PUBLIC_HOLIDAY")
                            .sourceType("FIXED_RULE")
                            .active(true)
                            .build();
                    return Optional.of(h);
                });

        seedService.seedFixedHolidaysForYear(2026);

        // Total 8 saves (4 create + 4 upsert), no extra rows beyond the 4 dates.
        verify(holidayRepository, times(8)).save(any(Holiday.class));
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(holidayRepository, times(8)).findByHolidayDate(dateCaptor.capture());
        // Only ever queried the 4 fixed dates.
        assertThat(dateCaptor.getAllValues()).allSatisfy(date ->
                assertThat(date).isIn(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 9, 2)));
    }
}
