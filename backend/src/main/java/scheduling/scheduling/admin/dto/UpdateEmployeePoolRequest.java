package scheduling.scheduling.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateEmployeePoolRequest(@NotEmpty List<Long> employeeIds) {
}
