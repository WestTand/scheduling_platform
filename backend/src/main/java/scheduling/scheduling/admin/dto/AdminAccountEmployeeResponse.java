package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Employee;

public record AdminAccountEmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String status) {

    public static AdminAccountEmployeeResponse from(Employee employee) {
        return new AdminAccountEmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getStatus());
    }
}
