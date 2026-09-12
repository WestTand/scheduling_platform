# Shift Type Quota Category Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit shift type quota categories so backend shift types can map safely to OR-Tools quota categories.

**Architecture:** Extend the existing `ShiftType` entity and admin shift type DTO/service flow with a string `quotaCategory`. Keep validation in `AdminShiftTypeService` using the existing `ResponseStatusException` pattern and default create requests to `REGULAR`.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Bean Validation, JUnit 6, Mockito, AssertJ, MockMvc.

---

## Files

- Modify `backend/src/main/java/scheduling/scheduling/entity/ShiftType.java`: add `quotaCategory` field defaulting to `REGULAR`.
- Modify `backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java`: add optional `quotaCategory`.
- Modify `backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java`: add required `quotaCategory`.
- Modify `backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java`: expose `quotaCategory`.
- Modify `backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java`: normalize and validate category on create/update.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java`: cover default, all accepted categories, update, and invalid category.
- Modify `backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java`: adjust request/response JSON expectations.

## Task 1: Add Shift Type Quota Category

- [x] **Step 1: Update tests first**

In `AdminShiftTypeServiceTest.java`, update helper constructors to include quota category and add/adjust assertions:

```java
assertThat(response.quotaCategory()).isEqualTo("REGULAR");
```

Add tests:

```java
@Test
void createShiftTypeAcceptsQuotaCategory() {
    CreateShiftTypeRequest request = createRequest("Ngoai gio", false, false, 1, 3, "OVERTIME");
    when(shiftTypeRepository.existsByShiftName("Ngoai gio")).thenReturn(false);
    when(shiftTypeRepository.save(org.mockito.ArgumentMatchers.any(ShiftType.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    AdminShiftTypeResponse response = service.createShiftType(request);

    assertThat(response.quotaCategory()).isEqualTo("OVERTIME");
}

@Test
void updateShiftTypeRejectsInvalidQuotaCategory() {
    ShiftType shiftType = shiftType(10L, "Old", "ACTIVE", "REGULAR");
    UpdateShiftTypeRequest request = updateRequest("Old", false, false, 1, 3, "ACTIVE", "BAD");
    when(shiftTypeRepository.findById(10L)).thenReturn(Optional.of(shiftType));

    assertThatThrownBy(() -> service.updateShiftType(10L, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

- [x] **Step 2: Run failing targeted test**

From `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminShiftTypeServiceTest"
```

Expected: compile failure because DTO/entity/response do not have `quotaCategory` yet.

- [x] **Step 3: Implement entity and DTO field**

Add to `ShiftType.java`:

```java
@Column(name = "quota_category", nullable = false, length = 20, columnDefinition = "varchar(20) default 'REGULAR'")
@Builder.Default
private String quotaCategory = "REGULAR";
```

Add `quotaCategory` to create/update request records and response record. Response mapping must call `shiftType.getQuotaCategory()`.

- [x] **Step 4: Implement service validation**

In `AdminShiftTypeService.java`, add constants:

```java
private static final String REGULAR = "REGULAR";
private static final String OVERTIME = "OVERTIME";
private static final String BATHING = "BATHING";
private static final String ON_CALL = "ON_CALL";
```

Add helper:

```java
private static String validateQuotaCategory(String quotaCategory, boolean required) {
    if (!StringUtils.hasText(quotaCategory)) {
        if (required) {
            throw invalidRequest();
        }
        return REGULAR;
    }
    String normalized = quotaCategory.trim();
    if (!REGULAR.equals(normalized) && !OVERTIME.equals(normalized)
            && !BATHING.equals(normalized) && !ON_CALL.equals(normalized)) {
        throw invalidRequest();
    }
    return normalized;
}
```

Use it in create with `required=false` and update with `required=true`.

- [x] **Step 5: Run targeted tests green**

From `backend`:

```powershell
.\mvnw.cmd test "-Dtest=AdminShiftTypeServiceTest,AdminShiftTypeControllerTest"
```

Expected: shift type service/controller tests pass.

- [x] **Step 6: Update contract wording if needed**

Ensure `docs/backend-ai-scheduling-contract.md` states backend stores `REGULAR` and sends AI `quotaCategory: null` for regular shift types.

- [x] **Step 7: Run full backend verification**

From `backend`:

```powershell
.\mvnw.cmd clean test
```

Expected: all backend tests pass.

- [ ] **Step 8: Commit**

From repo root:

```powershell
git add backend/src/main/java/scheduling/scheduling/entity/ShiftType.java backend/src/main/java/scheduling/scheduling/admin/dto/CreateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/UpdateShiftTypeRequest.java backend/src/main/java/scheduling/scheduling/admin/dto/AdminShiftTypeResponse.java backend/src/main/java/scheduling/scheduling/admin/AdminShiftTypeService.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeServiceTest.java backend/src/test/java/scheduling/scheduling/admin/AdminShiftTypeControllerTest.java backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java docs/superpowers/plans/2026-09-12-shift-type-quota-category.md
git commit -m "feat: add shift type quota category"
```

## Self-Review

- Spec coverage: plan covers entity, create/update/list response, validation, tests, and contract mapping.
- Placeholder scan: no placeholders or ambiguous implementation steps.
- Type consistency: field name is consistently `quotaCategory`; valid values are `REGULAR`, `OVERTIME`, `BATHING`, `ON_CALL`.
