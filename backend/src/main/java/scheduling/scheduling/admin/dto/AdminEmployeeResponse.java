package scheduling.scheduling.admin.dto;

import scheduling.scheduling.entity.Employee;

public record AdminEmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String status,
        AdminEmployeeAccountResponse account) {

    public static AdminEmployeeResponse from(Employee employee) {
        return new AdminEmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getStatus(),
                AdminEmployeeAccountResponse.from(employee.getAccount()));
    }
}
