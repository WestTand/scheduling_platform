import { apiConfig } from '@/config/api'
import { employeeSeed } from '@/mocks/managementData'
import { backendEmployeeService } from '@/services/backendEmployeeService'
import type { Employee, EmployeeFormValues, PaginatedResult } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let employees: Employee[] = clone(employeeSeed)

export const employeeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string }) {
    if (!apiConfig.useMockApi) {
      return backendEmployeeService.list({ page, pageSize, search, status })
    }
    await delay(350)

    const filtered = employees.filter((employee) => {
      const matchesSearch = !search || [employee.employeeCode, employee.fullName, employee.email, employee.phone].some((value) => value.toLowerCase().includes(search.toLowerCase()))
      const matchesStatus = status === 'ALL' || employee.status === status
      return matchesSearch && matchesStatus
    })

    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / pageSize))
    const safePage = Math.min(page, totalPages)
    const startIndex = (safePage - 1) * pageSize

    const pageData = filtered.slice(startIndex, startIndex + pageSize)

    return {
      data: pageData,
      page: safePage,
      pageSize,
      total,
      totalPages,
    } satisfies PaginatedResult<Employee>
  },

  async getById(id: string) {
    if (!apiConfig.useMockApi) {
      return backendEmployeeService.getById(id)
    }
    await delay(200)
    return employees.find((employee) => employee.id === id) ?? null
  },

  async add(data: EmployeeFormValues) {
    if (!apiConfig.useMockApi) {
      return backendEmployeeService.add(data)
    }
    await delay(350)
    const exists = employees.some((employee) => employee.employeeCode.toLowerCase() === data.employeeCode.trim().toLowerCase())

    if (exists) {
      throw new Error('Mã nhân viên đã tồn tại trong hệ thống.')
    }

    const newEmployee: Employee = {
      id: `emp-${Date.now()}`,
      employeeCode: data.employeeCode.trim(),
      fullName: data.fullName.trim(),
      email: data.email.trim(),
      phone: data.phone.trim(),
      role: data.role,
      status: data.status,
      joinDate: data.joinDate,
      note: data.note?.trim() || undefined,
    }

    employees = [newEmployee, ...employees]
    return newEmployee
  },

  async update(id: string, data: EmployeeFormValues) {
    if (!apiConfig.useMockApi) {
      return backendEmployeeService.update(id, data)
    }
    await delay(300)
    const index = employees.findIndex((employee) => employee.id === id)

    if (index === -1) {
      throw new Error('Nhân viên không tồn tại.')
    }

    const duplicate = employees.some(
      (employee) => employee.id !== id && employee.employeeCode.toLowerCase() === data.employeeCode.trim().toLowerCase(),
    )

    if (duplicate) {
      throw new Error('Mã nhân viên đã tồn tại trong hệ thống.')
    }

    employees[index] = {
      ...employees[index],
      employeeCode: data.employeeCode.trim(),
      fullName: data.fullName.trim(),
      email: data.email.trim(),
      phone: data.phone.trim(),
      role: data.role,
      status: data.status,
      joinDate: data.joinDate,
      note: data.note?.trim() || undefined,
    }

    return employees[index]
  },

  async toggleStatus(id: string, currentUserId?: string) {
    if (!apiConfig.useMockApi) {
      const employee = await backendEmployeeService.getById(id)
      if (!employee) throw new Error('Nhân viên không tồn tại.')
      if (employee.status === 'ACTIVE') {
        await backendEmployeeService.lock(id)
      } else {
        await backendEmployeeService.unlock(id)
      }
      return backendEmployeeService.getById(id)
    }
    await delay(250)

    if (currentUserId && currentUserId === id) {
      throw new Error('Bạn không thể tạm ngưng hoặc xóa tài khoản đang đăng nhập.')
    }

    const index = employees.findIndex((employee) => employee.id === id)

    if (index === -1) {
      throw new Error('Nhân viên không tồn tại.')
    }

    employees[index] = {
      ...employees[index],
      status: employees[index].status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
    }

    return employees[index]
  },

  async remove(id: string, currentUserId?: string) {
    if (!apiConfig.useMockApi) {
      const employee = await backendEmployeeService.getById(id)
      if (!employee) throw new Error('Nhân viên không tồn tại.')
      await backendEmployeeService.lock(id)
      return { ...employee, status: 'INACTIVE' }
    }
    await delay(250)

    if (currentUserId && currentUserId === id) {
      throw new Error('Bạn không thể xóa tài khoản đang đăng nhập.')
    }

    const employee = employees.find((item) => item.id === id)

    if (!employee) {
      throw new Error('Nhân viên không tồn tại.')
    }

    employees = employees.filter((item) => item.id !== id)
    return employee
  },

  reset() {
    employees = clone(employeeSeed)
  },
}
