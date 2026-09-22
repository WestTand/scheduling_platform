package scheduling.scheduling.employee.dto;

import scheduling.scheduling.entity.Employee;

public record CoworkerResponse(Long id, String employeeCode, String fullName) {

    public static CoworkerResponse from(Employee employee) {
        return new CoworkerResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName());
    }
}
