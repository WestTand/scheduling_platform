package scheduling.scheduling.auth.dto;

import scheduling.scheduling.entity.Employee;

public record EmployeeResponse(Long id, String employeeCode, String fullName, String email, String phone, String status) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getStatus());
    }
}
