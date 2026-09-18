package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import scheduling.scheduling.entity.Holiday;
import scheduling.scheduling.repository.HolidayRepository;

class HolidaySeedServiceTest {

    private HolidayRepository holidayRepository;
    private ResourcePatternResolver resourceResolver;
    private ObjectMapper objectMapper;
    private HolidaySeedService seedService;

    @BeforeEach
    void setUp() {
        holidayRepository = mock(HolidayRepository.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        objectMapper = new ObjectMapper();
        seedService = new HolidaySeedService(holidayRepository, resourceResolver, objectMapper);
        when(holidayRepository.saveAndFlush(any(Holiday.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Resource jsonResource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private static Resource namedJsonResource(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public java.io.InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Test
    void fixedHolidaysGeneratedForRequestedYear() {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());

        seedService.seedFixedHolidaysForYear(2026);

        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository, times(4)).saveAndFlush(captor.capture());
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
        verify(holidayRepository, times(2)).saveAndFlush(captor.capture());
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

        verify(holidayRepository, times(1)).saveAndFlush(existing);
        assertThat(existing.getHolidayName()).isEqualTo("Quoc khanh (updated)");
        // FIXED_RULE is canonical and must be preserved on JSON overlap.
        assertThat(existing.getSourceType()).isEqualTo("FIXED_RULE");
        assertThat(existing.getActive()).isTrue();
    }

    @Test
    void fixedRulePreservedOnOverlapKeepsCanonicalSource() {
        Holiday existing = Holiday.builder()
                .holidayDate(LocalDate.of(2026, 1, 1))
                .holidayName("Tet Duong lich")
                .holidayYear(2026)
                .holidayType("PUBLIC_HOLIDAY")
                .sourceType("FIXED_RULE")
                .active(true)
                .build();
        when(holidayRepository.findByHolidayDate(LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(existing));
        String json = """
                [
                  {"date": "2026-01-01", "name": "Tet Duong lich", "type": "PUBLIC_HOLIDAY", "sourceReference": "Lich nghi co dinh"}
                ]
                """;

        seedService.importAnnualHolidayResource("holidays-2026.json", jsonResource(json));

        verify(holidayRepository, times(1)).saveAndFlush(existing);
        assertThat(existing.getSourceType()).isEqualTo("FIXED_RULE");
        assertThat(existing.getHolidayName()).isEqualTo("Tet Duong lich");
        assertThat(existing.getHolidayType()).isEqualTo("PUBLIC_HOLIDAY");
        assertThat(existing.getHolidayYear()).isEqualTo(2026);
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
        verify(holidayRepository, times(4)).saveAndFlush(any(Holiday.class));

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
        verify(holidayRepository, times(8)).saveAndFlush(any(Holiday.class));
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

    @Test
    void usesInjectedObjectMapper() throws Exception {
        ObjectMapper injected = mock(ObjectMapper.class);
        when(injected.readTree(any(java.io.InputStream.class)))
                .thenReturn(new ObjectMapper().readTree("[]"));
        HolidaySeedService service = new HolidaySeedService(holidayRepository, resourceResolver, injected);
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());

        service.importAnnualHolidayResource("holidays-2026.json", jsonResource("[]"));

        verify(injected, times(1)).readTree(any(java.io.InputStream.class));
    }

    @Test
    void runContinuesWhenOneFileIsBad() throws Exception {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());
        String bad = """
                [
                  {"date": "2026-01-02", "name": "Weird", "type": "MOON_DAY"}
                ]
                """;
        String good = """
                [
                  {"date": "2026-02-16", "name": "Nghi Tet", "type": "LUNAR_HOLIDAY"}
                ]
                """;
        // Return out of order to also exercise filename sorting; bad file must not block good file.
        Resource badRes = namedJsonResource("holidays-2026.json", bad);
        Resource goodRes = namedJsonResource("holidays-2027.json", good.replace("2026-02-16", "2027-02-16"));
        when(resourceResolver.getResources("classpath*:holidays/holidays-*.json"))
                .thenReturn(new Resource[]{badRes, goodRes});

        seedService.run(mock(ApplicationArguments.class));

        // Fixed holidays for current+next year (8 saves) + 1 good annual row; bad file skipped.
        verify(holidayRepository, times(9)).saveAndFlush(any(Holiday.class));
    }

    @Test
    void concurrentInsertConflictIsNonFatal() {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());
        Holiday created = Holiday.builder()
                .holidayDate(LocalDate.of(2026, 2, 16))
                .holidayName("Nghi Tet")
                .holidayYear(2026)
                .holidayType("LUNAR_HOLIDAY")
                .sourceType("ANNUAL_SEED")
                .active(true)
                .build();
        when(holidayRepository.saveAndFlush(any(Holiday.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(holidayRepository.findByHolidayDate(LocalDate.of(2026, 2, 16)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        String json = """
                [
                  {"date": "2026-02-16", "name": "Nghi Tet", "type": "LUNAR_HOLIDAY"}
                ]
                """;

        // Must not propagate DataIntegrityViolationException; re-fetches after conflict.
        seedService.importAnnualHolidayResource("holidays-2026.json", jsonResource(json));

        verify(holidayRepository, times(2)).findByHolidayDate(LocalDate.of(2026, 2, 16));
    }

    @Test
    void runSkipsResourceWithMissingFilenameAndContinues() throws Exception {
        when(holidayRepository.findByHolidayDate(any(LocalDate.class))).thenReturn(Optional.empty());
        Resource unnamed = namedJsonResource(null, "[]");
        Resource named = namedJsonResource("holidays-2026.json", """
                [
                  {"date": "2026-02-16", "name": "Nghi Tet", "type": "LUNAR_HOLIDAY"}
                ]
                """);
        when(resourceResolver.getResources("classpath*:holidays/holidays-*.json"))
                .thenReturn(new Resource[]{unnamed, named});

        seedService.run(mock(ApplicationArguments.class));

        verify(holidayRepository, times(9)).saveAndFlush(any(Holiday.class));
    }

    @Test
    void unreadableFileRejects() {
        Resource broken = mock(Resource.class);
        when(broken.getFilename()).thenReturn("holidays-2026.json");
        try {
            when(broken.getInputStream()).thenThrow(new IOException("boom"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertThatThrownBy(() -> seedService.importAnnualHolidayResource("holidays-2026.json", broken))
                .isInstanceOf(IllegalStateException.class);
    }
}
