package scheduling.scheduling.employee;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ScheduleWindows {

    private ScheduleWindows() {
    }

    public record Window(LocalDate from, LocalDate to) {
    }

    public static Window resolve(Clock clock, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(30);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid schedule date range");
        }
        return new Window(start, end);
    }
}
