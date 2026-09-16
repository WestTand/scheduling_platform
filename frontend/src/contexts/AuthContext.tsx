import {
  createContext,
  useCallback,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'

import { clearStoredAuth, mockLogin, restoreStoredUser, setStoredAuth } from '@/services/authService'
import type { AuthContextValue, AuthState, LoginCredentials } from '@/types/auth'

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const initializeAuthState = (): AuthState => {
  const storedUser = restoreStoredUser()

  return {
    user: storedUser,
    isAuthenticated: Boolean(storedUser),
    isLoading: false,
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [authState, setAuthState] = useState<AuthState>(initializeAuthState)

  const login = useCallback(async (credentials: LoginCredentials, rememberMe: boolean) => {
    setAuthState((current) => ({ ...current, isLoading: true }))

    const user = await mockLogin(credentials)

    setStoredAuth(user, rememberMe)
    setAuthState({
      user,
      isAuthenticated: true,
      isLoading: false,
    })
  }, [])

  const logout = useCallback(() => {
    clearStoredAuth()
    setAuthState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
    })
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      ...authState,
      login,
      logout,
    }),
    [authState, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

