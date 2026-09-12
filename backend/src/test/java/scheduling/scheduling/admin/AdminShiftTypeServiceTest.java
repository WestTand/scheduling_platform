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

    @ParameterizedTest
    @ValueSource(strings = { "REGULAR", "OVERTIME", "BATHING", "ON_CALL" })
    void createShiftTypeAcceptsQuotaCategory(String quotaCategory) {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 1, 3, quotaCategory);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
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
                quotaCategory);
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
                quotaCategory);
    }

    private static ShiftType shiftType(Long id, String name, String status) {
        ShiftType shiftType = ShiftType.builder()
                .shiftName(name)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .durationHours(BigDecimal.valueOf(4.0))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(3)
                .quotaCategory("REGULAR")
                .status(status)
                .build();
        shiftType.setId(id);
        return shiftType;
    }
}
