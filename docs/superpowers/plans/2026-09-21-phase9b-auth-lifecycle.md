# Phase 9B Auth Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the frontend auth lifecycle for pending activation and forced password change using existing backend auth endpoints.

**Architecture:** Keep `authService` as the UI boundary and add backend lifecycle calls behind it. `LoginPage` routes backend pending states to `AccountActivationPage` through `location.state`, while `AccountActivationPage` stays mode-aware so mock mode still uses `accountService` and backend mode uses `authService`.

**Tech Stack:** React 19, TypeScript 6, Vite 8, React Router 7, native `fetch`, existing auth API client/storage.

---

## File Map

Create:

- None.

Modify:

- `frontend/src/types/backendAuth.ts` - add activation state DTO and guard.
- `frontend/src/services/backendAuthService.ts` - add OTP verify and forced password-change API calls.
- `frontend/src/services/authService.ts` - add mock/backend lifecycle methods and auth persistence after password change.
- `frontend/src/pages/auth/LoginPage.tsx` - catch pending login state and route to auth lifecycle page.
- `frontend/src/pages/auth/AccountActivationPage.tsx` - make the page mode-aware and wire backend lifecycle calls.
- `docs/project_plan.md` - mark only completed auth lifecycle items.

Do not add a public resend OTP endpoint in this phase.

---

### Task 1: Add Backend Auth Lifecycle DTOs And Service Calls

**Files:**

- Modify: `frontend/src/types/backendAuth.ts`
- Modify: `frontend/src/services/backendAuthService.ts`

- [ ] **Step 1: Extend backend auth DTO types**

In `frontend/src/types/backendAuth.ts`, add this after `BackendLoginStateResponse`:

```ts
export interface BackendActivationStateResponse {
  status: 'PASSWORD_CHANGE_REQUIRED'
  accountId: number
  username: string
}
```

Add this guard after `isBackendLoginStateResponse`:

```ts
export const isBackendActivationStateResponse = (value: unknown): value is BackendActivationStateResponse => {
  return Boolean(
    value &&
      typeof value === 'object' &&
      'status' in value &&
      'accountId' in value &&
      'username' in value &&
      (value as { status?: unknown }).status === 'PASSWORD_CHANGE_REQUIRED' &&
      typeof (value as { accountId?: unknown }).accountId === 'number' &&
      typeof (value as { username?: unknown }).username === 'string',
  )
}
```

- [ ] **Step 2: Add backend service imports**

In `frontend/src/services/backendAuthService.ts`, update the backend auth import to include `BackendActivationStateResponse` and `isBackendActivationStateResponse`:

```ts
import {
  isBackendActivationStateResponse,
  isBackendLoginStateResponse,
  type BackendActivationStateResponse,
  type BackendAuthResponse,
  type BackendLoginStateResponse,
  type BackendMeResponse,
} from '@/types/backendAuth'
```

- [ ] **Step 3: Add response validation helper**

In `frontend/src/services/backendAuthService.ts`, add this after `BackendLoginStateError`:

```ts
const requireActivationState = (response: BackendActivationStateResponse) => {
  if (!isBackendActivationStateResponse(response)) {
    throw new Error('Trạng thái kích hoạt tài khoản không hợp lệ.')
  }

  return response
}
```

- [ ] **Step 4: Add OTP verify and password-change methods**

In `frontend/src/services/backendAuthService.ts`, add these methods inside `backendAuthService`, after `login` and before `me`:

```ts
  async verifyActivationOtp(accountId: number, otp: string): Promise<BackendActivationStateResponse> {
    const response = await requestJson<BackendActivationStateResponse>('/api/auth/activation/verify-otp', {
      method: 'POST',
      auth: false,
      body: {
        accountId,
        otp,
      },
    })

    return requireActivationState(response)
  },

  async changeRequiredPassword(
    accountId: number,
    currentPassword: string,
    newPassword: string,
  ): Promise<BackendLoginResult> {
    const response = await requestJson<BackendAuthResponse>('/api/auth/password/change-required', {
      method: 'POST',
      auth: false,
      body: {
        accountId,
        currentPassword,
        newPassword,
      },
    })

    return {
      accessToken: response.accessToken,
      user: adaptBackendAccountToUser(response.account),
    }
  },
```

- [ ] **Step 5: Run build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: `tsc -b && vite build` succeeds.

- [ ] **Step 6: Commit**

```powershell
git add frontend/src/types/backendAuth.ts frontend/src/services/backendAuthService.ts
git commit -m "feat: add backend auth lifecycle calls"
```

---

### Task 2: Add Auth Service Lifecycle Boundary Methods

**Files:**

- Modify: `frontend/src/services/authService.ts`

- [ ] **Step 1: Add lifecycle methods to `authService`**

In `frontend/src/services/authService.ts`, add these methods inside `authService`, after `login` and before `getCurrentUser`:

```ts
  async verifyActivationOtp(accountId: string | number, otp: string): Promise<{ accountId: string; username: string }> {
    if (apiConfig.useMockApi) {
      const result = await accountService.verifyActivationOtp(String(accountId), otp)
      return {
        accountId: result.id,
        username: result.username,
      }
    }

    const result = await backendAuthService.verifyActivationOtp(Number(accountId), otp)
    return {
      accountId: String(result.accountId),
      username: result.username,
    }
  },

  async completeRequiredPasswordChange(
    accountId: string | number,
    currentPassword: string,
    newPassword: string,
    rememberMe: boolean,
  ): Promise<User> {
    if (apiConfig.useMockApi) {
      const account = await accountService.setNewPassword(String(accountId), newPassword)
      const user = await accountService.getUserForAccount(account.id)

      if (!user) {
        throw new Error('Không tìm thấy thông tin nhân viên cho tài khoản này.')
      }

      setStoredAuth(user, rememberMe)
      return user
    }

    const result = await backendAuthService.changeRequiredPassword(Number(accountId), currentPassword, newPassword)
    setStoredAuth(result.user, rememberMe, result.accessToken)
    return result.user
  },
```

- [ ] **Step 2: Add named exports for compatibility**

At the bottom of `frontend/src/services/authService.ts`, add these exports after `bootstrapAuth` and before `logout`:

```ts
export const verifyActivationOtp = (accountId: string | number, otp: string): Promise<{ accountId: string; username: string }> =>
  authService.verifyActivationOtp(accountId, otp)
export const completeRequiredPasswordChange = (
  accountId: string | number,
  currentPassword: string,
  newPassword: string,
  rememberMe: boolean,
): Promise<User> => authService.completeRequiredPasswordChange(accountId, currentPassword, newPassword, rememberMe)
```

- [ ] **Step 3: Run build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```powershell
git add frontend/src/services/authService.ts
git commit -m "feat: add auth lifecycle boundary"
```

---

### Task 3: Route Login Pending States To Auth Lifecycle

**Files:**

- Modify: `frontend/src/pages/auth/LoginPage.tsx`

- [ ] **Step 1: Import pending-state error type**

In `frontend/src/pages/auth/LoginPage.tsx`, add this import with the other service/type imports:

```ts
import { BackendLoginStateError } from '@/services/backendAuthService'
```

- [ ] **Step 2: Handle pending login state in submit catch**

Replace the current `catch` block in `handleSubmit` with:

```ts
    } catch (loginError) {
      if (loginError instanceof BackendLoginStateError) {
        navigate('/activate-account', {
          state: {
            accountId: String(loginError.state.accountId),
            username: loginError.state.username,
            currentPassword: credentials.password,
            status: loginError.state.status,
            rememberMe,
          },
        })
        return
      }

      setError(loginError instanceof Error ? loginError.message : 'Đăng nhập thất bại.')
    }
```

- [ ] **Step 3: Run build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```powershell
git add frontend/src/pages/auth/LoginPage.tsx
git commit -m "feat: route pending login states"
```

---

### Task 4: Wire Account Activation Page To Auth Service

**Files:**

- Modify: `frontend/src/pages/auth/AccountActivationPage.tsx`

- [ ] **Step 1: Update imports**

Replace the first import line with:

```ts
import { ArrowLeft, Eye, EyeOff, LockKeyhole, Mail, ShieldCheck } from 'lucide-react'
```

Replace the React Router import with:

```ts
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
```

Add these imports after UI imports:

```ts
import { apiConfig } from '@/config/api'
import { useAuth } from '@/contexts/useAuth'
import { BackendLoginStateError } from '@/services/backendAuthService'
import { authService } from '@/services/authService'
import { accountService, MOCK_OTP } from '@/services/accountService'
import { Role } from '@/types/auth'
```

Remove the old standalone `accountService, MOCK_OTP` import if it remains duplicated.

- [ ] **Step 2: Add route state type and redirect helper**

After `const activationDelaySeconds = 60`, add:

```ts
interface ActivationRouteState {
  accountId?: string
  username?: string
  currentPassword?: string
  status?: 'OTP_REQUIRED' | 'PASSWORD_CHANGE_REQUIRED'
  rememberMe?: boolean
}

const dashboardPathForRole = (role: Role) => {
  return role === Role.MANAGER ? '/manager/dashboard' : '/employee/dashboard'
}
```

- [ ] **Step 3: Initialize page from login route state**

Inside `AccountActivationPage`, after `const navigate = useNavigate()`, add:

```ts
  const location = useLocation()
  const { login } = useAuth()
  const routeState = (location.state ?? {}) as ActivationRouteState
  const initialBackendStep = routeState.status === 'PASSWORD_CHANGE_REQUIRED' ? 3 : routeState.status === 'OTP_REQUIRED' ? 2 : 1
```

Replace the existing state initializers for `step`, `username`, `temporaryPassword`, and `accountId` with:

```ts
  const [step, setStep] = useState<1 | 2 | 3>(apiConfig.useMockApi ? 1 : initialBackendStep)
  const [username, setUsername] = useState(routeState.username ?? searchParams.get('username') ?? '')
  const [temporaryPassword, setTemporaryPassword] = useState(routeState.currentPassword ?? '')
  const [accountId, setAccountId] = useState(routeState.accountId ?? '')
```

Keep the remaining state declarations unchanged.

- [ ] **Step 4: Make request OTP backend-aware**

Replace `handleRequestOtp` with:

```ts
  const handleRequestOtp = async (event?: FormEvent) => {
    event?.preventDefault()
    setError('')
    setSuccessMessage('')

    if (!username.trim() || !temporaryPassword) {
      setError('Vui lòng nhập tên tài khoản và mật khẩu tạm thời.')
      return
    }

    setLoading(true)
    try {
      if (!apiConfig.useMockApi) {
        try {
          await login({ email: username, password: temporaryPassword }, Boolean(routeState.rememberMe))
          navigate('/', { replace: true })
          return
        } catch (loginError) {
          if (!(loginError instanceof BackendLoginStateError)) {
            throw loginError
          }

          setAccountId(String(loginError.state.accountId))
          setUsername(loginError.state.username)

          if (loginError.state.status === 'PASSWORD_CHANGE_REQUIRED') {
            setStep(3)
            setSuccessMessage('Tài khoản cần đổi mật khẩu trước khi tiếp tục.')
            return
          }

          setStep(2)
          setOtp('')
          setCountdown(activationDelaySeconds)
          setSuccessMessage('Vui lòng nhập mã OTP đã được gửi tới email của bạn.')
          return
        }
      }

      const result = await accountService.requestActivation(username, temporaryPassword)
      setAccountId(result.account.id)
      setMaskedEmail(result.maskedEmail)
      setStep(2)
      setOtp('')
      setCountdown(activationDelaySeconds)
      setSuccessMessage('Mã OTP đã được gửi tới email của bạn.')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Thông tin tài khoản không hợp lệ hoặc tài khoản không thể kích hoạt.')
    } finally {
      setLoading(false)
    }
  }
```

- [ ] **Step 5: Make OTP verification backend-aware**

Replace the `try` block in `handleVerifyOtp` with:

```ts
    try {
      if (apiConfig.useMockApi) {
        const result = await accountService.verifyActivationOtp(accountId, otp)
        setAccountId(result.id)
        setUsername(result.username)
      } else {
        const result = await authService.verifyActivationOtp(accountId, otp)
        setAccountId(result.accountId)
        setUsername(result.username)
      }

      setStep(3)
      setSuccessMessage('Xác thực OTP thành công. Vui lòng đặt mật khẩu mới.')
```

Keep the existing `catch` and `finally` blocks.

- [ ] **Step 6: Make resend backend-aware**

Replace the `try` block in `handleResendOtp` with:

```ts
    try {
      if (!apiConfig.useMockApi) {
        setSuccessMessage('Vui lòng liên hệ quản lý để gửi lại mã OTP.')
        setCountdown(activationDelaySeconds)
        return
      }

      await accountService.resendActivationOtp(accountId)
      setCountdown(activationDelaySeconds)
      setOtp('')
      setSuccessMessage('Mã OTP mới đã được gửi lại. OTP mock hiện đang sử dụng: 123456.')
```

Keep the existing `catch` and `finally` blocks.

- [ ] **Step 7: Make password change backend-aware**

Replace the `try` block in `handleSetPassword` with:

```ts
    try {
      if (apiConfig.useMockApi) {
        await accountService.setNewPassword(accountId, newPassword)
        setSuccessMessage('Kích hoạt tài khoản thành công. Bạn có thể đăng nhập bằng mật khẩu mới.')
        setStep(1)
        setUsername('')
        setTemporaryPassword('')
        setOtp('')
        setNewPassword('')
        setConfirmPassword('')
        setAccountId('')
        setMaskedEmail('')
        return
      }

      const user = await authService.completeRequiredPasswordChange(
        accountId,
        temporaryPassword,
        newPassword,
        Boolean(routeState.rememberMe),
      )
      navigate(dashboardPathForRole(user.role), { replace: true })
```

Keep the existing `catch` and `finally` blocks.

- [ ] **Step 8: Hide backend mock OTP hint and clarify resend copy**

Replace the mock OTP paragraph near the resend button with:

```tsx
            {apiConfig.useMockApi ? (
              <p className="text-xs text-slate-500">OTP mock đang được sử dụng trong môi trường dev: {MOCK_OTP}</p>
            ) : (
              <p className="text-xs text-slate-500">Nếu chưa nhận được OTP, vui lòng liên hệ quản lý để gửi lại mã.</p>
            )}
```

- [ ] **Step 9: Run build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build succeeds.

- [ ] **Step 10: Commit**

```powershell
git add frontend/src/pages/auth/AccountActivationPage.tsx
git commit -m "feat: wire account activation lifecycle"
```

---

### Task 5: Backend-Mode Verification And Project Plan Update

**Files:**

- Modify: `docs/project_plan.md`

- [ ] **Step 1: Run default build**

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build succeeds.

- [ ] **Step 2: Run backend-mode build**

Run from `frontend/`:

```powershell
$env:VITE_USE_MOCK_API="false"; $env:VITE_API_BASE_URL="http://localhost:8080"; npm.cmd run build
```

Expected: build succeeds.

- [ ] **Step 3: Update project plan**

In `docs/project_plan.md`, mark these Phase 9 auth lifecycle items complete only if implementation is complete:

```markdown
- [x] Tao trang nhap OTP kich hoat tai khoan
- [x] Tao trang doi mat khau bat buoc lan dau
```

Do not mark resend OTP complete because this phase does not add public resend.

- [ ] **Step 4: Run whitespace check**

Run from repo root:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 5: Commit docs**

```powershell
git add docs/project_plan.md
git commit -m "docs: mark phase 9b auth lifecycle progress"
```

---

## Final Verification

Run from `frontend/`:

```powershell
npm.cmd run build
```

Expected: build succeeds.

Run from `frontend/`:

```powershell
$env:VITE_USE_MOCK_API="false"; $env:VITE_API_BASE_URL="http://localhost:8080"; npm.cmd run build
```

Expected: build succeeds.

Run from repo root:

```powershell
git diff --check
git status --short --branch
```

Expected: whitespace check passes; branch is `phase9b/auth-lifecycle`; only unrelated untracked docs remain.

Backend tests are not required for this phase unless backend files are modified.
