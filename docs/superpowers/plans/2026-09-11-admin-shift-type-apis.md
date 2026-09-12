# Admin Shift Type APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Phase 3 admin shift type list/create/update/lock/unlock APIs with validation and tests.

**Architecture:** Add a focused admin shift type module parallel to existing admin employee/account modules. Use a `ShiftTypeRepository` for optional filtering and duplicate-name checks, `AdminShiftTypeService` for validation/business logic, and `AdminShiftTypeController` for secured HTTP endpoints.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Security method authorization, Spring Data JPA, Jakarta Bean Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java`: Spring Data repository, optional search query, duplicate helpers.
- Create `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeNotFoundException.java`: missing shift type exception.
- Modify `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`: map `ShiftTypeNotFoundException` to `404`.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java`: response DTO.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java`: create request validation.
- Create `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java`: update request validation.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java`: list/create/update/lock/unlock logic.
- Create `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeController.java`: secured REST endpoints.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java`: service tests.
- Create `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java`: controller/security tests.
- Modify `docs/project_plan.md`: mark shift type APIs/tests complete after implementation.

## Task 1: Repository, Exception, and DTOs

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeNotFoundException.java`
- Modify: `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java`
- Create: `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java`

- [ ] **Step 1: Add repository**

Create `backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java`:

```java
package scheduling.scheduling.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import scheduling.scheduling.entity.ShiftType;

public interface ShiftTypeRepository extends JpaRepository<ShiftType, Long> {

    boolean existsByShiftName(String shiftName);

    boolean existsByShiftNameAndIdNot(String shiftName, Long id);

    @Query("""
            select s
            from ShiftType s
            where (:query is null or lower(s.shiftName) like lower(concat('%', :query, '%')))
              and (:status is null or s.status = :status)
            """)
    List<ShiftType> search(@Param("query") String query, @Param("status") String status);
}
```

- [ ] **Step 2: Add missing shift type exception**

Create `backend/src/main/java/scheduling/scheduling/admin/ShiftTypeNotFoundException.java`:

```java
package scheduling.scheduling.admin;

public class ShiftTypeNotFoundException extends RuntimeException {

    public ShiftTypeNotFoundException() {
        super("Shift type not found");
    }
}
```

- [ ] **Step 3: Add exception handler mapping**

Modify `backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java` by adding an import:

```java
import scheduling.scheduling.admin.ShiftTypeNotFoundException;
```

Then add this handler after the `EmployeeNotFoundException` handler:

```java
    @ExceptionHandler(ShiftTypeNotFoundException.class)
    ResponseEntity<ErrorResponse> handleShiftTypeNotFound(ShiftTypeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SHIFT_TYPE_NOT_FOUND", ex.getMessage()));
    }
```

- [ ] **Step 4: Add response DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java`:

```java
package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import scheduling.scheduling.entity.ShiftType;

public record AdminShiftTypeResponse(
        Long id,
        String shiftName,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal durationHours,
        Boolean overnight,
        Boolean heavy,
        Integer minStaff,
        Integer maxStaff,
        String status) {

    public static AdminShiftTypeResponse from(ShiftType shiftType) {
        return new AdminShiftTypeResponse(
                shiftType.getId(),
                shiftType.getShiftName(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                shiftType.getDurationHours(),
                shiftType.getOvernight(),
                shiftType.getHeavy(),
                shiftType.getMinStaff(),
                shiftType.getMaxStaff(),
                shiftType.getStatus());
    }
}
```

- [ ] **Step 5: Add create request DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateShiftTypeRequest(
        @NotBlank @Size(max = 50) String shiftName,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin("0.1") BigDecimal durationHours,
        Boolean overnight,
        Boolean heavy,
        @NotNull @Min(1) Integer minStaff,
        @NotNull @Min(1) Integer maxStaff) {
}
```

- [ ] **Step 6: Add update request DTO**

Create `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java`:

```java
package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

public record UpdateShiftTypeRequest(
        @NotBlank @Size(max = 50) String shiftName,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @DecimalMin("0.1") BigDecimal durationHours,
        @NotNull Boolean overnight,
        @NotNull Boolean heavy,
        @NotNull @Min(1) Integer minStaff,
        @NotNull @Min(1) Integer maxStaff,
        @NotBlank @Size(max = 20) String status) {
}
```

- [ ] **Step 7: Compile check**

Run: `backend\\mvnw.cmd test -DskipTests`

Expected: compilation succeeds.

- [ ] **Step 8: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/repository/ShiftTypeRepository.java backend/src/main/java/scheduling/scheduling/admin/ShiftTypeNotFoundException.java backend/src/main/java/scheduling/scheduling/auth/AuthExceptionHandler.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java
```

## Task 2: Admin Shift Type Service

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java`:

```java
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
        CreateShiftTypeRequest request = createRequest("Ngoai gio", null, null, 1, 3);
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
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createShiftTypeRejectsDuplicateName() {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 1, 3);
        when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(true);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift type name already exists");

        verify(shiftTypeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createShiftTypeRejectsMaxStaffLessThanMinStaff() {
        CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 4, 3);

        assertThatThrownBy(() -> service.createShiftType(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateShiftTypeChangesEditableFields() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("New", true, true, 2, 4, "INACTIVE");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(false);
        when(shiftTypeRepository.save(shiftType)).thenReturn(shiftType);

        AdminShiftTypeResponse response = service.updateShiftType(10L, request);

        assertThat(response.shiftName()).isEqualTo("New");
        assertThat(response.overnight()).isTrue();
        assertThat(response.heavy()).isTrue();
        assertThat(response.minStaff()).isEqualTo(2);
        assertThat(response.maxStaff()).isEqualTo(4);
        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void updateShiftTypeRejectsDuplicateNameOwnedByAnotherShiftType() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("New", false, false, 1, 3, "ACTIVE");
        when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));
        when(shiftTypeRepository.existsByShiftNameAndIdNot("New", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateShiftType(10L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Shift type name already exists");
    }

    @Test
    void updateShiftTypeRejectsInvalidStatus() {
        ShiftType shiftType = shiftType(10L, "Old", "ACTIVE");
        UpdateShiftTypeRequest request = updateRequest("Old", false, false, 1, 3, "DISABLED");
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

    private static CreateShiftTypeRequest createRequest(String name, Boolean overnight, Boolean heavy, int minStaff, int maxStaff) {
        return new CreateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                overnight,
                heavy,
                minStaff,
                maxStaff);
    }

    private static UpdateShiftTypeRequest updateRequest(String name, Boolean overnight, Boolean heavy, int minStaff, int maxStaff, String status) {
        return new UpdateShiftTypeRequest(
                name,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                overnight,
                heavy,
                minStaff,
                maxStaff,
                status);
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
                .status(status)
                .build();
        shiftType.setId(id);
        return shiftType;
    }
}
```

- [ ] **Step 2: Run service tests and verify RED**

Run: `backend\\mvnw.cmd test "-Dtest=AdminShiftTypeServiceTest"`

Expected: compilation fails because `AdminShiftTypeService` does not exist.

- [ ] **Step 3: Implement service**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java`:

```java
package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;
import scheduling.scheduling.auth.DuplicateResourceException;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Service
public class AdminShiftTypeService {

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final ShiftTypeRepository shiftTypeRepository;

    public AdminShiftTypeService(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminShiftTypeResponse> listShiftTypes(String query, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? validateStatus(status.trim()) : null;
        return shiftTypeRepository.search(normalizedQuery, normalizedStatus).stream()
                .map(AdminShiftTypeResponse::from)
                .toList();
    }

    @Transactional
    public AdminShiftTypeResponse createShiftType(CreateShiftTypeRequest request) {
        validateStaffRange(request.minStaff(), request.maxStaff());
        if (shiftTypeRepository.existsByShiftName(request.shiftName())) {
            throw new DuplicateResourceException("Shift type name already exists");
        }

        ShiftType shiftType = ShiftType.builder()
                .shiftName(request.shiftName())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .durationHours(request.durationHours())
                .overnight(Boolean.TRUE.equals(request.overnight()))
                .heavy(Boolean.TRUE.equals(request.heavy()))
                .minStaff(request.minStaff())
                .maxStaff(request.maxStaff())
                .status(ACTIVE)
                .build();
        return AdminShiftTypeResponse.from(shiftTypeRepository.save(shiftType));
    }

    @Transactional
    public AdminShiftTypeResponse updateShiftType(Long shiftTypeId, UpdateShiftTypeRequest request) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        validateStaffRange(request.minStaff(), request.maxStaff());
        String status = validateStatus(request.status().trim());
        if (shiftTypeRepository.existsByShiftNameAndIdNot(request.shiftName(), shiftTypeId)) {
            throw new DuplicateResourceException("Shift type name already exists");
        }

        shiftType.setShiftName(request.shiftName());
        shiftType.setStartTime(request.startTime());
        shiftType.setEndTime(request.endTime());
        shiftType.setDurationHours(request.durationHours());
        shiftType.setOvernight(request.overnight());
        shiftType.setHeavy(request.heavy());
        shiftType.setMinStaff(request.minStaff());
        shiftType.setMaxStaff(request.maxStaff());
        shiftType.setStatus(status);
        return AdminShiftTypeResponse.from(shiftTypeRepository.save(shiftType));
    }

    @Transactional
    public void lockShiftType(Long shiftTypeId) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        shiftType.setStatus(INACTIVE);
        shiftTypeRepository.save(shiftType);
    }

    @Transactional
    public void unlockShiftType(Long shiftTypeId) {
        ShiftType shiftType = findShiftType(shiftTypeId);
        shiftType.setStatus(ACTIVE);
        shiftTypeRepository.save(shiftType);
    }

    private ShiftType findShiftType(Long shiftTypeId) {
        return shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(ShiftTypeNotFoundException::new);
    }

    private static void validateStaffRange(Integer minStaff, Integer maxStaff) {
        if (minStaff != null && maxStaff != null && maxStaff < minStaff) {
            throw invalidRequest();
        }
    }

    private static String validateStatus(String status) {
        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw invalidRequest();
        }
        return status;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shift type request");
    }
}
```

- [ ] **Step 4: Run service tests and verify GREEN**

Run: `backend\\mvnw.cmd test "-Dtest=AdminShiftTypeServiceTest"`

Expected: all service tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java
```

## Task 3: Admin Shift Type Controller

**Files:**
- Create: `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeController.java`
- Test: `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java`:

```java
package scheduling.scheduling.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;
import scheduling.scheduling.auth.AuthExceptionHandler;
import scheduling.scheduling.auth.AuthService;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.auth.JwtAuthenticationFilter;
import scheduling.scheduling.auth.JwtService;
import scheduling.scheduling.auth.SecurityConfig;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.repository.AccountRepository;

@WebMvcTest(AdminShiftTypeController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AuthExceptionHandler.class })
@TestPropertySource(properties = {
        "app.auth.refresh-cookie-name=refresh_token",
        "app.auth.refresh-cookie-secure=false"
})
class AdminShiftTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShiftTypeService adminShiftTypeService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void managerListsShiftTypes() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.listShiftTypes("ngoai", "ACTIVE"))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/admin/shift-types")
                        .param("query", "ngoai")
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].shiftName").value("Ngoai gio"));
    }

    @Test
    void managerCreatesShiftType() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.createShiftType(any(CreateShiftTypeRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/admin/shift-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": "Ngoai gio",
                                  "startTime": "08:00:00",
                                  "endTime": "12:00:00",
                                  "durationHours": 4.0,
                                  "minStaff": 1,
                                  "maxStaff": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void managerUpdatesShiftType() throws Exception {
        givenToken("manager-token", manager());
        when(adminShiftTypeService.updateShiftType(any(Long.class), any(UpdateShiftTypeRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/admin/shift-types/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shiftName": "Ngoai gio",
                                  "startTime": "08:00:00",
                                  "endTime": "12:00:00",
                                  "durationHours": 4.0,
                                  "overnight": false,
                                  "heavy": false,
                                  "minStaff": 1,
                                  "maxStaff": 3,
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftName").value("Ngoai gio"));
    }

    @Test
    void managerLocksAndUnlocksShiftType() throws Exception {
        givenToken("manager-token", manager());

        mockMvc.perform(post("/api/admin/shift-types/10/lock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/shift-types/10/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
                .andExpect(status().isNoContent());

        verify(adminShiftTypeService).lockShiftType(10L);
        verify(adminShiftTypeService).unlockShiftType(10L);
    }

    @Test
    void employeeCannotAccessShiftTypeApis() throws Exception {
        givenToken("employee-token", employee());

        mockMvc.perform(get("/api/admin/shift-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminShiftTypeService);
    }

    private void givenToken(String token, CurrentAccountPrincipal principal) {
        when(jwtService.parseAccessToken(token)).thenReturn(principal);
        when(accountRepository.findById(principal.accountId())).thenReturn(Optional.of(account(principal)));
    }

    private static CurrentAccountPrincipal manager() {
        return new CurrentAccountPrincipal(1L, "manager01", AccountRole.MANAGER);
    }

    private static CurrentAccountPrincipal employee() {
        return new CurrentAccountPrincipal(2L, "employee01", AccountRole.EMPLOYEE);
    }

    private static Account account(CurrentAccountPrincipal principal) {
        Account account = Account.builder()
                .username(principal.username())
                .passwordHash("hash")
                .role(principal.role())
                .status(AccountStatus.ACTIVE)
                .isActive(true)
                .build();
        account.setId(principal.accountId());
        return account;
    }

    private static AdminShiftTypeResponse response() {
        return new AdminShiftTypeResponse(
                10L,
                "Ngoai gio",
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                BigDecimal.valueOf(4.0),
                false,
                false,
                1,
                3,
                "ACTIVE");
    }
}
```

- [ ] **Step 2: Run controller tests and verify RED**

Run: `backend\\mvnw.cmd test "-Dtest=AdminShiftTypeControllerTest"`

Expected: compilation fails because `AdminShiftTypeController` does not exist.

- [ ] **Step 3: Implement controller**

Create `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeController.java`:

```java
package scheduling.scheduling.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.admin.dto.AdminShiftTypeResponse;
import scheduling.scheduling.admin.dto.CreateShiftTypeRequest;
import scheduling.scheduling.admin.dto.UpdateShiftTypeRequest;

@RestController
@RequestMapping("/api/admin/shift-types")
public class AdminShiftTypeController {

    private final AdminShiftTypeService adminShiftTypeService;

    public AdminShiftTypeController(AdminShiftTypeService adminShiftTypeService) {
        this.adminShiftTypeService = adminShiftTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<List<AdminShiftTypeResponse>> listShiftTypes(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminShiftTypeService.listShiftTypes(query, status));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminShiftTypeResponse> createShiftType(@Valid @RequestBody CreateShiftTypeRequest request) {
        return ResponseEntity.status(201).body(adminShiftTypeService.createShiftType(request));
    }

    @PutMapping("/{shiftTypeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<AdminShiftTypeResponse> updateShiftType(
            @PathVariable Long shiftTypeId,
            @Valid @RequestBody UpdateShiftTypeRequest request) {
        return ResponseEntity.ok(adminShiftTypeService.updateShiftType(shiftTypeId, request));
    }

    @PostMapping("/{shiftTypeId}/lock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> lockShiftType(@PathVariable Long shiftTypeId) {
        adminShiftTypeService.lockShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{shiftTypeId}/unlock")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    ResponseEntity<Void> unlockShiftType(@PathVariable Long shiftTypeId) {
        adminShiftTypeService.unlockShiftType(shiftTypeId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run controller tests and verify GREEN**

Run: `backend\\mvnw.cmd test "-Dtest=AdminShiftTypeControllerTest"`

Expected: all controller tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeController.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java
```

## Task 4: Documentation and Verification

**Files:**
- Modify: `docs/project_plan.md`

- [ ] **Step 1: Update Phase 3 progress**

In `docs/project_plan.md`, mark these Phase 3 items complete:

```markdown
- [x] API quan ly loai ca truc
- [x] API them loai ca
- [x] API cap nhat loai ca
- [x] API khoa/mo khoa loai ca
- [x] Test shift type APIs
```

Keep scheduling rule items unchecked.

- [ ] **Step 2: Run full backend verification**

Run: `backend\\mvnw.cmd clean test`

Expected: build success with all tests passing.

- [ ] **Step 3: Commit docs**

Run:

```powershell
git add -- docs/project_plan.md
```

- [ ] **Step 4: Final status check**

Run: `git status --short --branch`

Expected: clean branch ahead of `origin/main` by the new commits.

## Self-Review

- Spec coverage: list/create/update/lock/unlock endpoints, role protection, duplicate handling, validation, not-found mapping, service/controller tests, and project plan updates are covered by Tasks 1-4.
- Placeholder scan: no unfinished placeholder markers remain.
- Type consistency: repository, service, controller, DTO, exception, and test names match across tasks.
