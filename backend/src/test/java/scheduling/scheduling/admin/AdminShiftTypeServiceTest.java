package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;
import scheduling.scheduling.auth.DuplicateResourceException;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

class AdminShiftTypeServiceTest {

    private final ShiftTypeRepository shiftTypeRepository = mock(ShiftTypeRepository.class);
    private final AdminShiftTypeService service = new AdminShiftTypeService(shiftTypeRepository);

    @Test
    void listShiftTypesNormalizesFilters() {
        ShiftType shiftType = shiftType(1L, "Ngoai gio", "ACTIVE");
        when(shiftTypeRepository.search("ngoai", "ACTIVE")).thenReturn(List.of(shiftType));

        List<AdminShiftTypeResponse> response = service.listShiftTypes(" ngoai ", " ACTIVE ");

        assertThat(response).extracting(AdminShiftTypeResponse::shiftName).containsExactly("Ngoai gio");
        verify(shiftTypeRepository).search("ngoai", "ACTIVE");
    }

    @Test
    void createShiftTypeDefaultsBooleansAndSavesActive() {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", null, null, 1, 3, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> {
                    ShiftType saved = invocation.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.overnight()).isFalse();
        assertThat(response.heavy()).isFalse();
        assertThat(response.quotaCategory()).isEqualTo("REGULAR");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createShiftTypeAppliesDefaultsForNewRuleFields() {
        CreateShiftTypeRequest request = new CreateShiftTypeRequest(
                "Ngoai gio",
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                null,
                null,
                1,
                3,
                null,
                "NGOAI_GIO",
                null,
                null,
                null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.shiftCode()).isEqualTo("NGOAI_GIO");
        assertThat(response.applicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI,SAT,SUN");
        assertThat(response.appliesOnHolidays()).isFalse();
        assertThat(response.penaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    void createShiftTypeAcceptsAndReturnsShiftCode() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "THONG_TAM", "MON,TUE", false, new BigDecimal("1.20"));
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("THONG_TAM")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.shiftCode()).isEqualTo("THONG_TAM");
        assertThat(response.applicableWeekdays()).isEqualTo("MON,TUE");
        assertThat(response.appliesOnHolidays()).isFalse();
        assertThat(response.penaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.20"));
    }

    @Test
    void updateShiftTypeAcceptsAndReturnsShiftCode() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequestWithCode(
                "New", "TRUC_SANG", "SAT,SUN", true, new BigDecimal("1.50"), "ACTIVE", "ON_CALL");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot("TRUC_SANG", 10L)).thenReturn(false);
        when(shiftTypeRepository.save(shiftType)).thenReturn(shiftType);

        AdminShiftTypeResponse response = service.updateShiftType(10L, request);

        assertThat(response.shiftCode()).isEqualTo("TRUC_SANG");
        assertThat(response.applicableWeekdays()).isEqualTo("SAT,SUN");
        assertThat(response.appliesOnHolidays()).isTrue();
        assertThat(response.penaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    void createShiftTypeRejectsDuplicateShiftCode() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "THONG_TAM", null, null, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("THONG_TAM")).thenReturn(true);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift code already exists");

        verify(shiftTypeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateShiftTypeRejectsDuplicateShiftCode() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        shiftType.setShiftCode("OLD_CODE");
        UpdateShiftTypeRequest request = updateRequestWithCode(
                "New", "THONG_TAM", "MON", false, new BigDecimal("1.00"), "ACTIVE", "REGULAR");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot("THONG_TAM", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift code already exists");
    }

    @ParameterizedTest
    @ValueSource(strings = { "thong_tam", "THONG-TAM", "THONG TAM", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345" })
    void createShiftTypeRejectsInvalidShiftCode(String shiftCode) {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", shiftCode, null, null, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode(shiftCode)).thenReturn(false);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "thong_tam", "THONG-TAM", "THONG TAM" })
    void updateShiftTypeRejectsInvalidShiftCode(String shiftCode) {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequestWithCode(
                "New", shiftCode, "MON", false, new BigDecimal("1.00"), "ACTIVE", "REGULAR");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot(shiftCode, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createShiftTypeNormalizesWeekdays() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "NGOAI_GIO", "fri,mon,mon", false, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.applicableWeekdays()).isEqualTo("MON,FRI");
    }

    @Test
    void createShiftTypeRejectsInvalidWeekdayToken() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "NGOAI_GIO", "MON,FUNDAY", false, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createShiftTypeRejectsBlankWeekdaysUnlessAppliesOnHolidays() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "NGOAI_GIO", "  ", false, null);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createShiftTypeAllowsBlankWeekdaysWhenAppliesOnHolidays() {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Truc Tet", "TRUC_TET", "", true, null);
        when(shiftTypeRepository.existsByShiftName("Truc Tet")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("TRUC_TET")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.applicableWeekdays()).isEqualTo("");
        assertThat(response.appliesOnHolidays()).isTrue();
    }

    @Test
    void createShiftTypeRejectsDurationOverEightHours() {
        CreateShiftTypeRequest request = new CreateShiftTypeRequest(
                "Ngoai gio",
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                BigDecimal.valueOf(8.5),
                false,
                false,
                1,
                3,
                "REGULAR",
                "NGOAI_GIO",
                "MON",
                false,
                BigDecimal.valueOf(1.0));
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "-0.01", "10.00" })
    void createShiftTypeRejectsMultiplierOutsideRange(String multiplier) {
        CreateShiftTypeRequest request = createRequestWithCode(
                "Ngoai gio", "NGOAI_GIO", "MON", false, new BigDecimal(multiplier));
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1.00", "10.00" })
    void updateShiftTypeRejectsMultiplierOutsideRange(String multiplier) {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequestWithCode(
                "New", "NEW_CODE", "MON", false, new BigDecimal(multiplier), "ACTIVE", "REGULAR");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot("NEW_CODE", 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = { "REGULAR", "OVERTIME", "BATHING", "ON_CALL" })
    void createShiftTypeAcceptsQuotaCategory(String quotaCategory) {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 1, 3, quotaCategory);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCode("NGOAI_GIO")).thenReturn(false);
        when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminShiftTypeResponse response = service.createShiftType(request);

        assertThat(response.quotaCategory()).isEqualTo(quotaCategory);
    }

    @ParameterizedTest
    @ValueSource(strings = { "REGULAR", "OVERTIME", "BATHING", "ON_CALL" })
    void updateShiftTypeAcceptsQuotaCategory(String quotaCategory) {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("Old", false, false, 1, 3, "ACTIVE", quotaCategory);
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("Old", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot("OLD_CODE", 10L)).thenReturn(false);
        when(shiftTypeRepository.save(shiftType)).thenReturn(shiftType);

        AdminShiftTypeResponse response = service.updateShiftType(10L, request);

        assertThat(response.quotaCategory()).isEqualTo(quotaCategory);
    }

    @Test
    void createShiftTypeRejectsDuplicateName() {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 1, 3, "REGULAR");
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(true);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift type name already exists");

        verify(shiftTypeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createShiftTypeRejectsMaxStaffLessThanMinStaff() {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 4, 3, "REGULAR");

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateShiftTypeChangesEditableFields() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("New", true, true, 2, 4, "INACTIVE", "BATHING");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.existsByShiftCodeAndIdNot("OLD_CODE", 10L)).thenReturn(false);
        when(shiftTypeRepository.save(shiftType)).thenReturn(shiftType);

        AdminShiftTypeResponse response = service.updateShiftType(10L, request);

        assertThat(response.shiftName()).isEqualTo("New");
        assertThat(response.overnight()).isTrue();
        assertThat(response.heavy()).isTrue();
        assertThat(response.minStaff()).isEqualTo(2);
        assertThat(response.maxStaff()).isEqualTo(4);
        assertThat(response.quotaCategory()).isEqualTo("BATHING");
        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void updateShiftTypeRejectsDuplicateNameOwnedByAnotherShiftType() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("New", false, false, 1, 3, "ACTIVE", "REGULAR");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift type name already exists");
    }

    @Test
    void updateShiftTypeRejectsInvalidStatus() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("Old", false, false, 1, 3, "DISABLED", "REGULAR");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateShiftTypeRejectsInvalidQuotaCategory() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("Old", false, false, 1, 3, "ACTIVE", "BAD");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void lockShiftTypeSetsInactive() {
        ShiftType shiftType = shiftType(10L, "Ngoai gio", "ACTIVE");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));

        service.lockShiftType(10L);

        assertThat(shiftType.getStatus()).isEqualTo("INACTIVE");
        verify(shiftTypeRepository).save(shiftType);
    }

    @Test
    void unlockShiftTypeSetsActive() {
        ShiftType shiftType = shiftType(10L, "Ngoai gio", "INACTIVE");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));

        service.unlockShiftType(10L);

        assertThat(shiftType.getStatus()).isEqualTo("ACTIVE");
        verify(shiftTypeRepository).save(shiftType);
    }

    @Test
    void missingShiftTypeThrowsNotFound() {
        when(shiftTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lockShiftType(99L))
                .isInstanceOf(ShiftTypeNotFoundException.class)
                .hasMessage("Shift type not found");
    }

    private static CreateShiftTypeRequest createRequest(String name, Boolean overnight, Boolean heavy, int minStaff, int maxStaff, String quotaCategory) {
        return new CreateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                overnight,
                heavy,
                minStaff,
                maxStaff,
                quotaCategory,
                "NGOAI_GIO",
                null,
                null,
                null);
    }

    private static CreateShiftTypeRequest createRequestWithCode(
            String name, String shiftCode, String applicableWeekdays, Boolean appliesOnHolidays, BigDecimal multiplier) {
        return new CreateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                false,
                false,
                1,
                3,
                "REGULAR",
                shiftCode,
                applicableWeekdays,
                appliesOnHolidays,
                multiplier);
    }

    private static UpdateShiftTypeRequest updateRequest(String name, Boolean overnight, Boolean heavy, int minStaff, int maxStaff, String status, String quotaCategory) {
        return new UpdateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                overnight,
                heavy,
                minStaff,
                maxStaff,
                status,
                quotaCategory,
                "OLD_CODE",
                "MON,TUE,WED,THU,FRI,SAT,SUN",
                false,
                BigDecimal.valueOf(1.0));
    }

    private static UpdateShiftTypeRequest updateRequestWithCode(
            String name, String shiftCode, String applicableWeekdays, Boolean appliesOnHolidays, BigDecimal multiplier, String status, String quotaCategory) {
        return new UpdateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                false,
                false,
                1,
                3,
                status,
                quotaCategory,
                shiftCode,
                applicableWeekdays,
                appliesOnHolidays,
                multiplier);
    }

    private static ShiftType shiftType(Long id, String name, String status) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName(name)
                .shiftCode("OLD_CODE")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status(status)
                .applicableWeekdays("MON,TUE,WED,THU,FRI,SAT,SUN")
                .appliesOnHolidays(false)
                .penaltyWeightMultiplier(new BigDecimal("1.00"))
                .build();
        shiftType.setId(id);
        return shiftType;
    }
}
