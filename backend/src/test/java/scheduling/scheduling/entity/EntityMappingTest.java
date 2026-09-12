package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityMappingTest {

    @Test
    void entitiesUseDocumentedPrimaryKeyColumnNames() {
        Map<Class<?>, String> expectedIdColumns = Map.ofEntries(
                Map.entry(Account.class, "account_id"),
                Map.entry(Employee.class, "employee_id"),
                Map.entry(ShiftType.class, "shift_type_id"),
                Map.entry(SchedulePeriod.class, "period_id"),
                Map.entry(WorkShift.class, "work_shift_id"),
                Map.entry(ShiftAssignment.class, "assignment_id"),
                Map.entry(LeaveRequest.class, "leave_request_id"),
                Map.entry(Preference.class, "preference_id"),
                Map.entry(SchedulingRule.class, "rule_id"),
                Map.entry(OptimizationRun.class, "run_id"),
                Map.entry(SwapRequest.class, "swap_request_id"),
                Map.entry(Notification.class, "notification_id"),
                Map.entry(ActivityLog.class, "log_id"),
                Map.entry(RefreshToken.class, "refresh_token_id"),
                Map.entry(AccountActivationOtp.class, "activation_otp_id"));

        assertThat(expectedIdColumns).allSatisfy((entityClass, expectedColumnName) -> {
            AttributeOverride override = entityClass.getAnnotation(AttributeOverride.class);

            assertThat(override)
                    .as("%s should override BaseEntity.id", entityClass.getSimpleName())
                    .isNotNull();
            assertThat(override.name()).isEqualTo("id");
            assertThat(override.column().name()).isEqualTo(expectedColumnName);
        });
    }

    @Test
    void entitiesDeclareOnlyDocumentedAuditColumns() {
        assertThat(declaredColumnNames(Account.class)).contains("created_at").doesNotContain("updated_at");
        assertThat(declaredColumnNames(Employee.class)).contains("created_at", "updated_at");
        assertThat(declaredColumnNames(ShiftAssignment.class)).contains("created_at").doesNotContain("updated_at");
        assertThat(declaredColumnNames(Notification.class)).contains("created_at").doesNotContain("updated_at");
        assertThat(declaredColumnNames(ActivityLog.class)).contains("created_at").doesNotContain("updated_at");

        assertThat(declaredColumnNames(ShiftType.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(SchedulePeriod.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(WorkShift.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(LeaveRequest.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(Preference.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(SchedulingRule.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(OptimizationRun.class)).doesNotContain("created_at", "updated_at");
        assertThat(declaredColumnNames(SwapRequest.class)).doesNotContain("created_at", "updated_at");
    }

    @Test
    void refreshTokenDeclaresRequiredColumns() {
        assertThat(declaredColumnNames(RefreshToken.class))
                .contains("token_hash", "expires_at", "revoked_at", "created_at")
                .doesNotContain("updated_at");
    }

    @Test
    void accountActivationOtpDeclaresRequiredColumns() {
        assertThat(declaredColumnNames(AccountActivationOtp.class))
                .contains("otp_hash", "expires_at", "used_at", "created_at")
                .doesNotContain("updated_at");
    }

    @Test
    void refreshTokenEqualityUsesOnlyBaseEntityFields() {
        RefreshToken first = RefreshToken.builder()
                .account(Account.builder().username("alice").build())
                .tokenHash("first-token-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        RefreshToken second = RefreshToken.builder()
                .account(Account.builder().username("bob").build())
                .tokenHash("second-token-hash")
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        first.setId(1L);
        second.setId(1L);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void refreshTokenExpirationRequiresNow() {
        RefreshToken refreshToken = RefreshToken.builder()
                .expiresAt(LocalDateTime.now())
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> refreshToken.isExpired(null))
                .withMessage("now");
    }

    @Test
    void lombokBuildersPreserveEntityDefaults() {
        assertThat(Account.builder().build().getIsActive()).isTrue();
        assertThat(Account.builder().build().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(Employee.builder().build().getStatus()).isEqualTo("ACTIVE");
        assertThat(ShiftType.builder().build().getOvernight()).isFalse();
        assertThat(ShiftType.builder().build().getHeavy()).isFalse();
        assertThat(ShiftType.builder().build().getQuotaCategory()).isEqualTo("REGULAR");
        assertThat(ShiftType.builder().build().getStatus()).isEqualTo("ACTIVE");
        assertThat(SchedulePeriod.builder().build().getVersionNo()).isEqualTo(1);
        assertThat(SchedulePeriod.builder().build().getStatus()).isEqualTo("DRAFT");
        assertThat(ShiftAssignment.builder().build().getAssignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(ShiftAssignment.builder().build().getAssignedSource()).isEqualTo("AUTO");
        assertThat(ShiftAssignment.builder().build().getLocked()).isFalse();
        assertThat(LeaveRequest.builder().build().getStatus()).isEqualTo("PENDING");
        assertThat(Preference.builder().build().getPreferenceType()).isEqualTo("WANT");
        assertThat(Preference.builder().build().getPriority()).isEqualTo(1);
        assertThat(Preference.builder().build().getStatus()).isEqualTo("PENDING");
        assertThat(SchedulingRule.builder().build().getActive()).isTrue();
        assertThat(SwapRequest.builder().build().getStatus()).isEqualTo("PENDING_TARGET");
        assertThat(Notification.builder().build().getRead()).isFalse();
    }

    @Test
    void accountStatusIsStoredAsRequiredEnumColumn() throws NoSuchFieldException {
        Field status = Account.class.getDeclaredField("status");

        assertThat(status.getType()).isEqualTo(AccountStatus.class);
        assertThat(status.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(status.getAnnotation(Column.class).name()).isEqualTo("status");
        assertThat(status.getAnnotation(Column.class).nullable()).isFalse();
    }

    @Test
    void accountRolesIncludeSystemAdminManagerAndEmployee() {
        assertThat(AccountRole.values())
                .containsExactly(AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.EMPLOYEE);
        assertThat(AccountRole.ADMIN.getValue()).isEqualTo("ADMIN");
    }

    private static Iterable<String> declaredColumnNames(Class<?> entityClass) {
        return java.util.Arrays.stream(entityClass.getDeclaredFields())
                .map(field -> field.getAnnotation(Column.class))
                .filter(java.util.Objects::nonNull)
                .map(Column::name)
                .toList();
    }
}
