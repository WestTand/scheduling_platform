package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ShiftTypeRules {

    public static final String DEFAULT_WEEKDAYS = "MON,TUE,WED,THU,FRI,SAT,SUN";
    public static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("1.00");
    public static final BigDecimal MIN_MULTIPLIER = new BigDecimal("0.00");
    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("9.99");
    public static final BigDecimal MAX_DURATION = new BigDecimal("8.0");

    private static final List<String> ISO_ORDER = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private static final java.util.regex.Pattern SHIFT_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]{0,29}$");

    private ShiftTypeRules() {
    }

    public static String validateShiftCode(String shiftCode) {
        if (shiftCode == null || shiftCode.isBlank()) {
            throw invalidRequest();
        }
        String trimmed = shiftCode.trim();
        if (!SHIFT_CODE_PATTERN.matcher(trimmed).matches()) {
            throw invalidRequest();
        }
        return trimmed;
    }

    public static String normalizeWeekdays(String applicableWeekdays, boolean appliesOnHolidays) {
        if (applicableWeekdays == null) {
            return DEFAULT_WEEKDAYS;
        }
        String trimmed = applicableWeekdays.trim();
        if (trimmed.isEmpty()) {
            if (!appliesOnHolidays) {
                throw invalidRequest();
            }
            return "";
        }
        String[] parts = trimmed.split(",");
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : parts) {
            String token = part.trim().toUpperCase();
            if (token.isEmpty()) {
                continue;
            }
            if (!ISO_ORDER.contains(token)) {
                throw invalidRequest();
            }
            normalized.add(token);
        }
        if (normalized.isEmpty()) {
            if (!appliesOnHolidays) {
                throw invalidRequest();
            }
            return "";
        }
        List<String> ordered = new ArrayList<>();
        for (String day : ISO_ORDER) {
            if (normalized.contains(day)) {
                ordered.add(day);
            }
        }
        return String.join(",", ordered);
    }

    public static BigDecimal validateDuration(BigDecimal durationHours) {
        if (durationHours == null) {
            throw invalidRequest();
        }
        if (durationHours.compareTo(BigDecimal.ZERO) <= 0
                || durationHours.compareTo(MAX_DURATION) > 0) {
            throw invalidRequest();
        }
        return durationHours;
    }

    public static BigDecimal normalizeMultiplier(BigDecimal multiplier) {
        BigDecimal value = multiplier == null ? DEFAULT_MULTIPLIER : multiplier;
        if (value.compareTo(MIN_MULTIPLIER) < 0 || value.compareTo(MAX_MULTIPLIER) > 0) {
            throw invalidRequest();
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean includesWeekday(String applicableWeekdays, DayOfWeek day) {
        if (applicableWeekdays == null || applicableWeekdays.isBlank()) {
            return false;
        }
        String token = day.name().substring(0, 3);
        for (String part : applicableWeekdays.split(",")) {
            if (token.equals(part.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift type request");
    }
}
