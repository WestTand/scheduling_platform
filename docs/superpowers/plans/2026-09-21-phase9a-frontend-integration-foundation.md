# Phase 9A Frontend Integration Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the frontend foundation for switching between mock data and real backend auth/API integration.

**Architecture:** Keep the existing mock-first UI intact while adding a small config module, a shared fetch API client, backend auth DTOs, and adapter functions. `authService` becomes the service boundary that chooses mock or backend mode, and `AuthContext` keeps its public contract while adding session bootstrap behavior.

**Tech Stack:** React 19, TypeScript 6, Vite 8, React Router 7, native `fetch`, existing local/session storage auth keys.

---

## File Map

Create:

- `frontend/src/config/api.ts` - Vite API mode config.
- `frontend/src/services/apiClient.ts` - shared fetch client and `ApiError`.
- `frontend/src/types/backendAuth.ts` - raw backend auth DTOs.
- `frontend/src/adapters/authAdapter.ts` - backend auth DTO to frontend `User` mapping.
- `frontend/src/services/backendAuthService.ts` - backend auth endpoint calls.

Modify:

- `frontend/src/types/auth.ts` - make logout async-compatible while preserving call sites.
- `frontend/src/services/authService.ts` - expose mock/backend-aware auth boundary and real token storage.
- `frontend/src/contexts/AuthContext.tsx` - bootstrap backend sessions and call async auth service methods.
- `frontend/src/pages/auth/LoginPage.tsx` - update autocomplete/type semantics from email-only to username-friendly without redesign.
- `docs/project_plan.md` - mark Phase 9A foundation items complete after implementation.

Do not migrate manager/employee feature pages in this plan.

---

### Task 1: Add API Runtime Config

**Files:**

- Create: `frontend/src/config/api.ts`

- [ ] **Step 1: Create config module**

Create `frontend/src/config/api.ts`:

```ts
const DEFAULT_API_BASE_URL = 'http://localhost:8080'

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

export const apiConfig = {
  apiBaseUrl: trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL),
  useMockApi: import.meta.env.VITE_USE_MOCK_API !== 'false',
}
```

- [ ] **Step 2: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: `tsc -b && vite build` succeeds.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/config/api.ts
git commit -m "feat: add frontend api config"
```

---

### Task 2: Add Shared Fetch API Client

**Files:**

- Create: `frontend/src/services/apiClient.ts`

- [ ] **Step 1: Create API client**

Create `frontend/src/services/apiClient.ts`:

```ts
import { apiConfig } from '@/config/api'
import { getStoredToken } from '@/services/authService'

const DEFAULT_ERROR_MESSAGE = 'Không thể kết nối máy chủ. Vui lòng thử lại.'

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  auth?: boolean
}

const buildUrl = (path: string) => {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }

  return `${apiConfig.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

const parseResponseBody = async (response: Response) => {
  const text = await response.text()

  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

const errorMessage = (body: unknown) => {
  if (typeof body === 'string' && body.trim()) {
    return body
  }

  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }

  return DEFAULT_ERROR_MESSAGE
}

const errorCode = (body: unknown) => {
  if (body && typeof body === 'object' && 'code' in body) {
    const code = (body as { code?: unknown }).code
    return typeof code === 'string' ? code : undefined
  }

  return undefined
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const hasBody = options.body !== undefined

  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.auth !== false) {
    const token = getStoredToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const response = await fetch(buildUrl(path), {
    ...options,
    headers,
    credentials: 'include',
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })

  const body = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return body as T
}

export async function requestVoid(path: string, options: RequestOptions = {}): Promise<void> {
  await requestJson<unknown>(path, options)
}
```

- [ ] **Step 2: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/services/apiClient.ts
git commit -m "feat: add frontend api client"
```

---

### Task 3: Add Backend Auth DTOs And Adapter

**Files:**

- Create: `frontend/src/types/backendAuth.ts`
- Create: `frontend/src/adapters/authAdapter.ts`

- [ ] **Step 1: Create backend auth DTO types**

Create `frontend/src/types/backendAuth.ts`:

```ts
export type BackendAccountRole = 'ADMIN' | 'MANAGER' | 'EMPLOYEE'

export interface BackendAccountResponse {
  id: number
  username: string
  role: BackendAccountRole
}

export interface BackendEmployeeResponse {
  id: number
  employeeCode: string
  fullName: string
  email: string
  phone: string
  status: string
}

export interface BackendAuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  account: BackendAccountResponse
}

export interface BackendMeResponse {
  account: BackendAccountResponse
  employee: BackendEmployeeResponse | null
}

export interface BackendLoginStateResponse {
  status: 'OTP_REQUIRED' | 'PASSWORD_CHANGE_REQUIRED'
  accountId: number
  username: string
}

export const isBackendLoginStateResponse = (value: unknown): value is BackendLoginStateResponse => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'status' in value &&
      ((value as { status?: unknown }).status === 'OTP_REQUIRED' ||
        (value as { status?: unknown }).status === 'PASSWORD_CHANGE_REQUIRED'),
  )
}
```

- [ ] **Step 2: Create auth adapter**

Create `frontend/src/adapters/authAdapter.ts`:

```ts
import { Role, type User } from '@/types/auth'
import type { BackendAccountResponse, BackendMeResponse } from '@/types/backendAuth'

const toFrontendRole = (role: BackendAccountResponse['role']) => {
  return role === 'EMPLOYEE' ? Role.EMPLOYEE : Role.MANAGER
}

export const adaptBackendMeToUser = (response: BackendMeResponse): User => {
  const employee = response.employee
  const displayName = employee?.fullName || response.account.username

  return {
    id: String(employee?.id ?? response.account.id),
    name: displayName,
    email: employee?.email || response.account.username,
    role: toFrontendRole(response.account.role),
    department: employee?.employeeCode || 'Scheduling',
    avatar: displayName.slice(0, 2).toUpperCase(),
  }
}

export const adaptBackendAccountToUser = (account: BackendAccountResponse): User => {
  return {
    id: String(account.id),
    name: account.username,
    email: account.username,
    role: toFrontendRole(account.role),
    department: 'Scheduling',
    avatar: account.username.slice(0, 2).toUpperCase(),
  }
}
```

- [ ] **Step 3: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```powershell
git add frontend/src/types/backendAuth.ts frontend/src/adapters/authAdapter.ts
git commit -m "feat: add frontend auth adapters"
```

---

### Task 4: Add Backend Auth Service

**Files:**

- Create: `frontend/src/services/backendAuthService.ts`

- [ ] **Step 1: Create backend auth service**

Create `frontend/src/services/backendAuthService.ts`:

```ts
import { adaptBackendAccountToUser, adaptBackendMeToUser } from '@/adapters/authAdapter'
import { requestJson, requestVoid } from '@/services/apiClient'
import type { LoginCredentials, User } from '@/types/auth'
import type { BackendAuthResponse, BackendLoginStateResponse, BackendMeResponse } from '@/types/backendAuth'
import { isBackendLoginStateResponse } from '@/types/backendAuth'

export interface BackendLoginResult {
  accessToken: string
  user: User
}

export interface BackendBootstrapResult {
  accessToken?: string
  user: User
}

export const backendAuthService = {
  async login(credentials: LoginCredentials): Promise<BackendLoginResult> {
    const response = await requestJson<BackendAuthResponse | BackendLoginStateResponse>('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: {
        username: credentials.email.trim(),
        password: credentials.password,
      },
    })

    if (isBackendLoginStateResponse(response)) {
      if (response.status === 'OTP_REQUIRED') {
        throw new Error('Tài khoản chưa được kích hoạt.')
      }

      throw new Error('Tài khoản cần đổi mật khẩu trước khi tiếp tục.')
    }

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },

  async me(): Promise<User> {
    const response = await requestJson<BackendMeResponse>('/api/auth/me')
    return adaptBackendMeToUser(response)
  },

  async refresh(): Promise<BackendBootstrapResult> {
    const response = await requestJson<BackendAuthResponse>('/api/auth/refresh', {
      method: 'POST',
      auth: false,
    })

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },

  async logout(): Promise<void> {
    await requestVoid('/api/auth/logout', {
      method: 'POST',
      auth: false,
    })
  },
}
```

- [ ] **Step 2: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/services/backendAuthService.ts
git commit -m "feat: add backend auth service"
```

---

### Task 5: Refactor Auth Service Boundary

**Files:**

- Modify: `frontend/src/services/authService.ts`
- Modify: `frontend/src/types/auth.ts`

- [ ] **Step 1: Update auth context type for async logout**

In `frontend/src/types/auth.ts`, change `logout` to allow async implementation:

```ts
export interface AuthContextValue extends AuthState {
  login: (credentials: LoginCredentials, rememberMe: boolean) => Promise<void>
  logout: () => Promise<void>
}
```

- [ ] **Step 2: Refactor authService exports**

Replace `frontend/src/services/authService.ts` with:

```ts
import { apiConfig } from '@/config/api'
import { mockUsers } from '@/mocks/users'
import { accountService } from '@/services/accountService'
import { backendAuthService } from '@/services/backendAuthService'
import type { LoginCredentials, User } from '@/types/auth'

const AUTH_DELAY_MS = 700

export const AUTH_STORAGE_KEYS = {
  TOKEN: 'schedule_auth_token',
  USER: 'schedule_auth_user',
}

const storageForRememberMe = (rememberMe: boolean) => (rememberMe ? localStorage : sessionStorage)

export const getStoredToken = () => {
  return localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ?? sessionStorage.getItem(AUTH_STORAGE_KEYS.TOKEN)
}

const getStoredUserStorage = () => {
  if (localStorage.getItem(AUTH_STORAGE_KEYS.USER)) {
    return localStorage
  }

  if (sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)) {
    return sessionStorage
  }

  return localStorage
}

export const setStoredAuth = (user: User, rememberMe: boolean, accessToken?: string) => {
  const token = accessToken ?? `mock-token-${user.role.toLowerCase()}-${user.id}`
  const storage = storageForRememberMe(rememberMe)

  localStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  localStorage.removeItem(AUTH_STORAGE_KEYS.USER)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.USER)

  storage.setItem(AUTH_STORAGE_KEYS.TOKEN, token)
  storage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
}

export const updateStoredAccessToken = (accessToken: string) => {
  const storage = localStorage.getItem(AUTH_STORAGE_KEYS.TOKEN) ? localStorage : sessionStorage
  storage.setItem(AUTH_STORAGE_KEYS.TOKEN, accessToken)
}

export const clearStoredAuth = () => {
  localStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  localStorage.removeItem(AUTH_STORAGE_KEYS.USER)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.TOKEN)
  sessionStorage.removeItem(AUTH_STORAGE_KEYS.USER)
}

export const restoreStoredUser = () => {
  const storedUser = localStorage.getItem(AUTH_STORAGE_KEYS.USER) ?? sessionStorage.getItem(AUTH_STORAGE_KEYS.USER)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser) as User
  } catch {
    return null
  }
}

const mockLogin = async (credentials: LoginCredentials): Promise<User> => {
  await new Promise((resolve) => {
    window.setTimeout(resolve, AUTH_DELAY_MS)
  })

  const input = credentials.email.trim()
  const legacyAccount = mockUsers.find(
    (user) => user.email.toLowerCase() === input.toLowerCase() || user.name.toLowerCase() === input.toLowerCase(),
  )

  const linkedAccount = await accountService.getAccountByIdentifier(input)
  const passwordMatch = linkedAccount ? await accountService.getPasswordForAccount(linkedAccount.id) === credentials.password : false

  if (legacyAccount && legacyAccount.password === credentials.password) {
    return {
      id: legacyAccount.id,
      name: legacyAccount.name,
      email: legacyAccount.email,
      role: legacyAccount.role,
      department: legacyAccount.department,
      avatar: legacyAccount.avatar,
    }
  }

  if (!linkedAccount || !passwordMatch) {
    throw new Error('Thông tin đăng nhập không đúng. Vui lòng kiểm tra lại email hoặc tên tài khoản và mật khẩu.')
  }

  if (linkedAccount.status === 'PENDING_ACTIVATION') {
    throw new Error('Tài khoản chưa được kích hoạt.')
  }

  if (linkedAccount.status === 'LOCKED' || linkedAccount.status === 'DISABLED') {
    throw new Error('Tài khoản này đã bị khóa hoặc vô hiệu hóa.')
  }

  if (linkedAccount.status !== 'ACTIVE') {
    throw new Error('Tài khoản chưa được kích hoạt.')
  }

  const employeeUser = await accountService.getUserForAccount(linkedAccount.id)

  if (!employeeUser) {
    throw new Error('Không tìm thấy thông tin nhân viên cho tài khoản này.')
  }

  return employeeUser
}

export const authService = {
  async login(credentials: LoginCredentials, rememberMe: boolean): Promise<User> {
    if (apiConfig.useMockApi) {
      const user = await mockLogin(credentials)
      setStoredAuth(user, rememberMe)
      return user
    }

    const result = await backendAuthService.login(credentials)
    setStoredAuth(result.user, rememberMe, result.accessToken)
    return result.user
  },

  async bootstrapAuth(): Promise<User | null> {
    const storedUser = restoreStoredUser()

    if (apiConfig.useMockApi) {
      return storedUser
    }

    if (!getStoredToken()) {
      return null
    }

    try {
      const user = await backendAuthService.me()
      const storage = getStoredUserStorage()
      storage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
      return user
    } catch {
      try {
        const refreshed = await backendAuthService.refresh()
        if (refreshed.accessToken) {
          updateStoredAccessToken(refreshed.accessToken)
        }
        const user = await backendAuthService.me().catch(() => refreshed.user)
        const storage = getStoredUserStorage()
        storage.setItem(AUTH_STORAGE_KEYS.USER, JSON.stringify(user))
        return user
      } catch {
        clearStoredAuth()
        return null
      }
    }
  },

  async logout(): Promise<void> {
    if (!apiConfig.useMockApi) {
      try {
        await backendAuthService.logout()
      } catch {
        // Local cleanup still wins; backend cookie may already be expired.
      }
    }

    clearStoredAuth()
  },
}
```

- [ ] **Step 3: Defer build until context update**

Do not run or commit after this task alone. `AuthContext` still imports the previous auth service functions, so Task 6 must be applied before the TypeScript build can be meaningful.

Run a local status check from repo root to confirm only intended auth files are changed:

```powershell
git status --short
```

Expected: changed files include `frontend/src/types/auth.ts` and `frontend/src/services/authService.ts`.

---

### Task 6: Update Auth Context Bootstrap And Logout

**Files:**

- Modify: `frontend/src/contexts/AuthContext.tsx`

- [ ] **Step 1: Update AuthContext**

Replace `frontend/src/contexts/AuthContext.tsx` with:

```tsx
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

    const bootstrap = async () => {
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

    void bootstrap()

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
```

- [ ] **Step 2: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit Tasks 5 and 6 together**

Commit the auth service boundary and context changes together because they must build together:

```powershell
git add frontend/src/types/auth.ts frontend/src/services/authService.ts frontend/src/contexts/AuthContext.tsx
git commit -m "feat: switch auth between mock and backend"
```

---

### Task 7: Username-Friendly Login Form Copy

**Files:**

- Modify: `frontend/src/pages/auth/LoginPage.tsx`

- [ ] **Step 1: Update input semantics only**

In `LoginPage.tsx`, keep state names unchanged but change the username field input:

```tsx
<input
  id="email"
  type="text"
  value={credentials.email}
  onChange={(event) => {
    setCredentials((current) => ({ ...current, email: event.target.value }))
    if (error) setError('')
  }}
  onBlur={() => setTouched((current) => ({ ...current, email: true }))}
  placeholder="Email hoặc tên đăng nhập"
  className="w-full border-0 bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
  autoComplete="username"
/>
```

- [ ] **Step 2: Run build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```powershell
git add frontend/src/pages/auth/LoginPage.tsx
git commit -m "fix: align login input with username auth"
```

---

### Task 8: Backend Mode Build Verification And Project Plan Update

**Files:**

- Modify: `docs/project_plan.md`

- [ ] **Step 1: Run default mock-mode build**

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

- [ ] **Step 2: Run backend-mode build**

Run from `frontend/`:

```powershell
$env:VITE_USE_MOCK_API="false"; $env:VITE_API_BASE_URL="http://localhost:8080"; npm run build
```

Expected: build succeeds.

- [ ] **Step 3: Update project plan**

In `docs/project_plan.md`, mark these Phase 9 items complete:

```markdown
- [x] Tao frontend HTTP client va API mode config
- [x] Tao backend DTO types va adapter functions trong frontend
```

Do not mark page-level migration items complete.

- [ ] **Step 4: Run whitespace check**

Run from repo root:

```powershell
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 5: Commit docs**

```powershell
git add docs/project_plan.md
git commit -m "docs: mark phase 9a frontend foundation progress"
```

- [ ] **Step 6: Final status**

Run from repo root:

```powershell
git status --short --branch
```

Expected: branch `phase9/frontend-integration`; only unrelated untracked docs remain.

---

## Final Verification

Run from `frontend/`:

```powershell
npm run build
```

Expected: build succeeds.

Run from `frontend/`:

```powershell
$env:VITE_USE_MOCK_API="false"; $env:VITE_API_BASE_URL="http://localhost:8080"; npm run build
```

Expected: build succeeds.

Run from repo root:

```powershell
git diff --check
git status --short --branch
```

Expected: whitespace check passes; only unrelated untracked docs remain.

Backend/AI tests are not required for Phase 9A unless backend or AI files are modified.
