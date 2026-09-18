package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;

class WorkShiftWindowServiceTest {

    private final WorkShiftWindowService service = new WorkShiftWindowService();

    @Test
    void sameDayShiftDerivesSameDayWindow() {
        ShiftType shiftType = shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), "8.0");

        WorkShiftWindowService.Window window =
                service.derive(LocalDate.of(2026, 9, 14), shiftType);

        assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
        assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
        assertThat(window.workMinutes()).isEqualTo(480);
        assertThat(window.transitionShift()).isFalse();
    }

    @Test
    void overnightShiftDerivesNextDayEnd() {
        ShiftType shiftType = shiftType(LocalTime.of(17, 0), LocalTime.of(0, 30), "7.5");

        WorkShiftWindowService.Window window =
                service.derive(LocalDate.of(2026, 9, 14), shiftType);

        assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T17:00:00+07:00"));
        assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-15T00:30:00+07:00"));
        assertThat(window.workMinutes()).isEqualTo(450);
        assertThat(window.transitionShift()).isTrue();
    }

    @Test
    void endExactlyMidnightDerivesNextDayEnd() {
        ShiftType shiftType = shiftType(LocalTime.of(16, 0), LocalTime.MIDNIGHT, "8.0");

        WorkShiftWindowService.Window window =
                service.derive(LocalDate.of(2026, 9, 14), shiftType);

        assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
        assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-15T00:00:00+07:00"));
        assertThat(window.workMinutes()).isEqualTo(480);
        assertThat(window.transitionShift()).isTrue();
    }

    @Test
    void earlyMorningShiftDerivesSameDayWindow() {
        ShiftType shiftType = shiftType(LocalTime.of(0, 30), LocalTime.of(8, 0), "7.5");

        WorkShiftWindowService.Window window =
                service.derive(LocalDate.of(2026, 9, 14), shiftType);

        assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T00:30:00+07:00"));
        assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
        assertThat(window.workMinutes()).isEqualTo(450);
        assertThat(window.transitionShift()).isFalse();
    }

    @Test
    void persistedCompleteWindowIsReturnedAsIs() {
        ShiftType shiftType = shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), "8.0");
        OffsetDateTime persistedStart = OffsetDateTime.parse("2026-09-14T08:00:00+07:00");
        OffsetDateTime persistedEnd = OffsetDateTime.parse("2026-09-14T16:00:00+07:00");
        WorkShift workShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 14))
                .shiftType(shiftType)
                .startsAt(persistedStart)
                .endsAt(persistedEnd)
                .workMinutes(480)
                .transitionShift(false)
                .build();

        WorkShiftWindowService.Window window = service.resolve(workShift);

        assertThat(window.startsAt()).isEqualTo(persistedStart);
        assertThat(window.endsAt()).isEqualTo(persistedEnd);
        assertThat(window.workMinutes()).isEqualTo(480);
        assertThat(window.transitionShift()).isFalse();
    }

    @Test
    void nullPersistedFieldCausesFullRecompute() {
        ShiftType shiftType = shiftType(LocalTime.of(8, 0), LocalTime.of(16, 0), "8.0");
        WorkShift workShift = WorkShift.builder()
                .workDate(LocalDate.of(2026, 9, 14))
                .shiftType(shiftType)
                .startsAt(OffsetDateTime.parse("2000-01-01T00:00:00+07:00"))
                .endsAt(OffsetDateTime.parse("2000-01-01T01:00:00+07:00"))
                .workMinutes(60)
                .transitionShift(null)
                .build();

        WorkShiftWindowService.Window window = service.resolve(workShift);

        assertThat(window.startsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T08:00:00+07:00"));
        assertThat(window.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-14T16:00:00+07:00"));
        assertThat(window.workMinutes()).isEqualTo(480);
        assertThat(window.transitionShift()).isFalse();
    }

    private static ShiftType shiftType(LocalTime start, LocalTime end, String durationHours) {
        return ShiftType.builder()
                .shiftName("Test")
                .startTime(start)
                .endTime(end)
                .durationHours(new BigDecimal(durationHours))
                .minStaff(1)
                .maxStaff(2)
                .build();
    }
}
