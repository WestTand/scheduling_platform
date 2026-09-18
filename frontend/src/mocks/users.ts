import { Role, type User } from '@/types/auth'

export interface MockUserAccount extends User {
  password: string
}

export const mockUsers: MockUserAccount[] = [
  {
    id: 'manager-01',
    name: 'Nguyễn Thị Lan',
    email: 'manager@schedule.local',
    password: '123456',
    role: Role.MANAGER,
    department: 'Quản lý vận hành',
    avatar: 'NL',
  },
  {
    id: 'employee-01',
    name: 'Trần Văn Nam',
    email: 'employee@schedule.local',
    password: '123456',
    role: Role.EMPLOYEE,
    department: 'Điều phối ca',
    avatar: 'TN',
  },
]
