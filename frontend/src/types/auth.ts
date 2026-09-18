export const Role = {
  MANAGER: 'MANAGER',
  EMPLOYEE: 'EMPLOYEE',
} as const

export type Role = (typeof Role)[keyof typeof Role]

export interface User {
  id: string
  name: string
  email: string
  role: Role
  department: string
  avatar?: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
}

export interface AuthContextValue extends AuthState {
  login: (credentials: LoginCredentials, rememberMe: boolean) => Promise<void>
  logout: () => void
}
