import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'

import { authService } from '@/services/authService'
import type { AuthContextValue, AuthState, LoginCredentials } from '@/types/auth'

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const initialAuthState: AuthState = {
  user: null,
  isAuthenticated: false,
  isLoading: true,
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [authState, setAuthState] = useState<AuthState>(initialAuthState)

  useEffect(() => {
    let active = true

    const bootstrapAuth = async () => {
      const user = await authService.bootstrapAuth()

      if (!active) {
        return
      }

      setAuthState({
        user,
        isAuthenticated: Boolean(user),
        isLoading: false,
      })
    }

    bootstrapAuth()

    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (credentials: LoginCredentials, rememberMe: boolean) => {
    setAuthState((current) => ({ ...current, isLoading: true }))

    try {
      const user = await authService.login(credentials, rememberMe)

      setAuthState({
        user,
        isAuthenticated: true,
        isLoading: false,
      })
    } catch (error) {
      setAuthState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
      })
      throw error
    }
  }, [])

  const logout = useCallback(async () => {
    setAuthState((current) => ({ ...current, isLoading: true }))

    await authService.logout()

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

