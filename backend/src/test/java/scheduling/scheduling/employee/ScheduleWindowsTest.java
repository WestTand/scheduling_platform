package scheduling.scheduling.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ScheduleWindowsTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMissingRangeToTodayThroughTodayPlusThirtyDays() {
        ScheduleWindows.Window window = ScheduleWindows.resolve(FIXED_CLOCK, null, null);

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void defaultsMissingToToFromPlusThirtyDays() {
        ScheduleWindows.Window window =
                ScheduleWindows.resolve(FIXED_CLOCK, LocalDate.of(2026, 9, 20), null);

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void defaultsMissingFromToToday() {
        ScheduleWindows.Window window =
                ScheduleWindows.resolve(FIXED_CLOCK, null, LocalDate.of(2026, 10, 1));

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void passesExplicitRangeThrough() {
        ScheduleWindows.Window window = ScheduleWindows.resolve(
                FIXED_CLOCK, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> ScheduleWindows.resolve(
                        FIXED_CLOCK, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
