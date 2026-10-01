package scheduling.scheduling.admin;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import scheduling.scheduling.entity.Holiday;
import scheduling.scheduling.repository.HolidayRepository;

@Component
@ConditionalOnProperty(name = "app.seed.legacy-runners.enabled", havingValue = "true")
@Order(2)
public class HolidaySeedService implements ApplicationRunner {

    // Boot 4 auto-configures tools.jackson ObjectMapper, not com.fasterxml.jackson;
    // provide a com.fasterxml.jackson bean so constructor injection works in prod.
    @Configuration
    static class HolidayJacksonConfig {
        @Bean
        @ConditionalOnMissingBean(ObjectMapper.class)
        ObjectMapper holidayObjectMapper() {
            return new ObjectMapper();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(HolidaySeedService.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern FILENAME_YEAR = Pattern.compile("holidays-(\\d{4})\\.json");
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "PUBLIC_HOLIDAY", "LUNAR_HOLIDAY", "SUBSTITUTE_HOLIDAY", "SPECIAL_HOLIDAY");
    private static final String FIXED_SOURCE = "FIXED_RULE";
    private static final String ANNUAL_SOURCE = "ANNUAL_SEED";
    private static final String FIXED_TYPE = "PUBLIC_HOLIDAY";

    private final HolidayRepository holidayRepository;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper objectMapper;

    public HolidaySeedService(HolidayRepository holidayRepository,
            ResourcePatternResolver resourceResolver,
            ObjectMapper objectMapper) {
        this.holidayRepository = holidayRepository;
        this.resourceResolver = resourceResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int currentYear = LocalDate.now(ZONE).getYear();
        seedFixedHolidaysForYear(currentYear);
        seedFixedHolidaysForYear(currentYear + 1);
        Resource[] resources = resourceResolver.getResources("classpath*:holidays/holidays-*.json");
        if (resources.length == 0) {
            log.info("No annual holiday files found under classpath*:holidays/holidays-*.json");
            return;
        }
        Arrays.sort(resources, Comparator.comparing(r -> {
            String name = r.getFilename();
            return name == null ? "" : name;
        }));
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                if (filename == null) {
                    throw new IllegalStateException("Holiday resource has no filename");
                }
                importAnnualHolidayResource(filename, resource);
            } catch (IllegalStateException ex) {
                // Non-fatal per-file: one bad file must not rollback fixed holidays or other files.
                log.error("Skipping holiday file {} due to bad input: {}", filename, ex.getMessage());
            }
        }
    }

    void seedFixedHolidaysForYear(int year) {
        for (FixedHoliday fixed : fixedHolidays(year)) {
            upsert(fixed.date(), fixed.name(), year, FIXED_TYPE, FIXED_SOURCE, null);
        }
    }

    void importAnnualHolidayResource(String filename, Resource resource) {
        int fileYear = parseFilenameYear(filename);
        JsonNode root = readJsonArray(filename, resource);
        for (JsonNode node : root) {
            String dateText = node.has("date") ? node.get("date").asText(null) : null;
            String name = node.has("name") ? node.get("name").asText(null) : null;
            String type = node.has("type") ? node.get("type").asText(null) : null;
            String sourceReference = node.has("sourceReference")
                    ? node.get("sourceReference").asText(null)
                    : null;
            if (dateText == null || dateText.isBlank()) {
                throw new IllegalStateException("Missing date in " + filename);
            }
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Missing name in " + filename + " for date " + dateText);
            }
            if (type == null || !ALLOWED_TYPES.contains(type)) {
                throw new IllegalStateException("Unknown holiday type in " + filename + ": " + type);
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dateText);
            } catch (DateTimeParseException ex) {
                throw new IllegalStateException(
                        "Unparsable holiday date in " + filename + ": " + dateText, ex);
            }
            if (date.getYear() != fileYear) {
                throw new IllegalStateException(
                        "Holiday date year mismatch in " + filename + ": " + dateText);
            }
            upsert(date, name, fileYear, type, ANNUAL_SOURCE, sourceReference);
        }
    }

    private void upsert(LocalDate date, String name, int year, String type,
            String sourceType, String sourceReference) {
        Holiday holiday = holidayRepository.findByHolidayDate(date).orElseGet(() -> Holiday.builder()
                .holidayDate(date)
                .build());
        boolean isOverlapOnFixed = FIXED_SOURCE.equals(holiday.getSourceType())
                && ANNUAL_SOURCE.equals(sourceType);
        holiday.setHolidayDate(date);
        holiday.setHolidayName(name);
        holiday.setHolidayYear(year);
        holiday.setHolidayType(type);
        if (isOverlapOnFixed) {
            // Fixed holidays are canonical: on JSON overlap, update name/type/year/active
            // but preserve FIXED_RULE (and its sourceReference) so Task3 fixed spec wins.
            // Overlap in holidays-2026.json is kept intentionally to exercise this upsert path.
            log.info("Preserving FIXED_RULE for overlapping holiday {} from annual seed", date);
        } else {
            holiday.setSourceType(sourceType);
            holiday.setSourceReference(sourceReference);
        }
        // Ensure fixed rows created via builder path get sourceType set.
        if (holiday.getSourceType() == null) {
            holiday.setSourceType(sourceType);
            holiday.setSourceReference(sourceReference);
        }
        holiday.setActive(true);
        try {
            holidayRepository.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException ex) {
            // Concurrency-safe upsert (same pattern as StandardShiftTypeSeeder.saveSafely):
            // another instance inserted the same date concurrently; re-fetch and skip create.
            log.warn("Concurrent seeding conflict for holiday date {}: {}. Re-fetching existing row.",
                    date, ex.getMessage());
            holidayRepository.findByHolidayDate(date)
                    .ifPresent(existing -> log.warn(
                            "Found existing holiday date {} after conflict, skipping create",
                            existing.getHolidayDate()));
            return;
        }
        log.info("Seeded holiday {}: {}", date, name);
    }

    private int parseFilenameYear(String filename) {
        Matcher matcher = FILENAME_YEAR.matcher(filename);
        if (!matcher.matches()) {
            throw new IllegalStateException("Cannot parse year from filename: " + filename);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private JsonNode readJsonArray(String filename, Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("Holiday file must contain a JSON array: " + filename);
            }
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read holiday file: " + filename, ex);
        }
    }

    private static List<FixedHoliday> fixedHolidays(int year) {
        return List.of(
                new FixedHoliday(LocalDate.of(year, 1, 1), "Tet Duong lich"),
                new FixedHoliday(LocalDate.of(year, 4, 30), "Ngay Giai phong mien Nam"),
                new FixedHoliday(LocalDate.of(year, 5, 1), "Ngay Quoc te Lao dong"),
                new FixedHoliday(LocalDate.of(year, 9, 2), "Quoc khanh"));
    }

    private record FixedHoliday(LocalDate date, String name) {
    }
}
