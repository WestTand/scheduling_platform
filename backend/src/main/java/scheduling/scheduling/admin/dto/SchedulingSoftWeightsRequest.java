package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SchedulingSoftWeightsRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal fairness,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal preference,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal weekend,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal heavyShift,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal workHourBalance) {
}
