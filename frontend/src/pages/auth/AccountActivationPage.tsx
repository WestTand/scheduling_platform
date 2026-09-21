import { ArrowLeft, Eye, EyeOff, LockKeyhole, Mail, ShieldCheck } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { OtpInput } from '@/components/account/OtpInput'
import { PasswordRequirements } from '@/components/account/PasswordRequirements'
import { Button } from '@/components/ui/button'
import { apiConfig } from '@/config/api'
import { useAuth } from '@/contexts/useAuth'
import { authService } from '@/services/authService'
import { BackendLoginStateError } from '@/services/backendAuthService'
import { accountService, MOCK_OTP } from '@/services/accountService'
import { Role } from '@/types/auth'

const activationDelaySeconds = 60

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

export function AccountActivationPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const routeState = (location.state ?? {}) as ActivationRouteState
  const initialBackendStep = routeState.status === 'PASSWORD_CHANGE_REQUIRED' ? 3 : routeState.status === 'OTP_REQUIRED' ? 2 : 1
  const [searchParams] = useSearchParams()
  const [step, setStep] = useState<1 | 2 | 3>(apiConfig.useMockApi ? 1 : initialBackendStep)
  const [username, setUsername] = useState(routeState.username ?? searchParams.get('username') ?? '')
  const [temporaryPassword, setTemporaryPassword] = useState(routeState.currentPassword ?? '')
  const [otp, setOtp] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showTemporaryPassword, setShowTemporaryPassword] = useState(false)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [accountId, setAccountId] = useState(routeState.accountId ?? '')
  const [maskedEmail, setMaskedEmail] = useState('')
  const [countdown, setCountdown] = useState(activationDelaySeconds)
  const clearedSensitiveStateRef = useRef(false)

  useEffect(() => {
    if (clearedSensitiveStateRef.current) {
      return
    }

    const pendingState = location.state as ActivationRouteState | null

    if (!pendingState?.currentPassword) {
      return
    }

    clearedSensitiveStateRef.current = true
    navigate(location.pathname + location.search, {
      replace: true,
      state: {
        accountId: pendingState.accountId,
        username: pendingState.username,
        status: pendingState.status,
        rememberMe: pendingState.rememberMe,
      } satisfies ActivationRouteState,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (step !== 2 || countdown <= 0) {
      return
    }

    const timer = window.setTimeout(() => {
      setCountdown((current) => Math.max(0, current - 1))
    }, 1000)

    return () => window.clearTimeout(timer)
  }, [countdown, step])

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

  const handleVerifyOtp = async () => {
    setError('')
    setSuccessMessage('')

    if (!accountId) {
      setError('Vui lòng gửi yêu cầu OTP trước.')
      return
    }

    if (otp.trim().length !== 6) {
      setError('Mã OTP phải đủ 6 chữ số.')
      return
    }

    setLoading(true)
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
    } catch (verifyError) {
      setError(verifyError instanceof Error ? verifyError.message : 'Mã OTP không hợp lệ.')
    } finally {
      setLoading(false)
    }
  }

  const handleResendOtp = async () => {
    if (!accountId) {
      return
    }

    setError('')
    setLoading(true)
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
    } catch (resendError) {
      setError(resendError instanceof Error ? resendError.message : 'Không thể gửi lại mã OTP.')
    } finally {
      setLoading(false)
    }
  }

  const handleSetPassword = async (event?: FormEvent) => {
    event?.preventDefault()
    setError('')

    if (!accountId) {
      setError('Thiếu thông tin tài khoản.')
      return
    }

    const passwordRules = [
      newPassword.length >= 8,
      /[A-Z]/.test(newPassword),
      /[a-z]/.test(newPassword),
      /\d/.test(newPassword),
    ]

    if (passwordRules.some((rule) => !rule)) {
      setError('Mật khẩu mới không đáp ứng đủ yêu cầu bảo mật.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('Xác nhận mật khẩu mới không khớp.')
      return
    }

    if (temporaryPassword && newPassword === temporaryPassword) {
      setError('Mật khẩu mới không được trùng với mật khẩu tạm thời.')
      return
    }

    setLoading(true)
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
    } catch (setPasswordError) {
      setError(setPasswordError instanceof Error ? setPasswordError.message : 'Không thể đặt mật khẩu mới.')
    } finally {
      setLoading(false)
    }
  }

  const canResendOtp = countdown === 0 && step === 2

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-xl sm:p-8">
        <div className="mb-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-100 text-blue-700">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <p className="text-sm font-medium text-slate-500">Kích hoạt tài khoản</p>
              <h1 className="text-2xl font-semibold text-slate-900">Bước {step} / 3</h1>
            </div>
          </div>
          <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900">
            <ArrowLeft className="h-4 w-4" />
            Đăng nhập
          </Link>
        </div>

        {step === 1 && (
          <form onSubmit={handleRequestOtp} className="space-y-5">
            <div className="space-y-2">
              <label htmlFor="activation-username" className="text-sm font-medium text-slate-700">Tên tài khoản</label>
              <input
                id="activation-username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
                autoComplete="username"
                placeholder="Nhập tên tài khoản"
              />
            </div>

            <div className="space-y-2">
              <label htmlFor="activation-temp-password" className="text-sm font-medium text-slate-700">Mật khẩu tạm thời</label>
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 focus-within:border-blue-500">
                <LockKeyhole className="h-4 w-4 text-slate-400" />
                <input
                  id="activation-temp-password"
                  type={showTemporaryPassword ? 'text' : 'password'}
                  value={temporaryPassword}
                  onChange={(event) => setTemporaryPassword(event.target.value)}
                  className="w-full border-0 bg-transparent text-sm focus:outline-none"
                  autoComplete="current-password"
                />
                <button type="button" onClick={() => setShowTemporaryPassword((current) => !current)} className="text-slate-500" aria-label={showTemporaryPassword ? 'Ẩn mật khẩu tạm thời' : 'Hiện mật khẩu tạm thời'}>
                  {showTemporaryPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
            {successMessage && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{successMessage}</div>}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Đang gửi...' : 'Gửi mã OTP'}
            </Button>
          </form>
        )}

        {step === 2 && (
          <div className="space-y-6">
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-center">
              <Mail className="mx-auto mb-2 h-5 w-5 text-blue-600" />
              <p className="text-sm text-slate-600">Mã OTP đã được gửi tới</p>
              <p className="mt-1 text-base font-semibold text-slate-900">{maskedEmail || 'email của bạn'}</p>
            </div>

            <div className="space-y-3">
              <label className="text-sm font-medium text-slate-700">Nhập mã OTP 6 chữ số</label>
              <OtpInput value={otp} onChange={setOtp} disabled={loading} />
            </div>

            <div className="flex items-center justify-between text-sm text-slate-600">
              <span>Hiệu lực: 5 phút</span>
              <span>{countdown}s</span>
            </div>

            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
            {successMessage && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{successMessage}</div>}

            <div className="grid gap-3 sm:grid-cols-2">
              <Button type="button" variant="outline" onClick={() => setStep(1)} disabled={loading}>
                Quay lại
              </Button>
              <Button type="button" onClick={handleVerifyOtp} disabled={loading || otp.trim().length !== 6}>
                {loading ? 'Đang xác nhận...' : 'Xác nhận OTP'}
              </Button>
            </div>

            <Button type="button" variant="ghost" className="w-full" onClick={handleResendOtp} disabled={loading || !canResendOtp}>
              {canResendOtp ? 'Gửi lại mã' : `Gửi lại mã (${countdown}s)`}
            </Button>

            {apiConfig.useMockApi ? (
              <p className="text-xs text-slate-500">OTP mock đang được sử dụng trong môi trường dev: {MOCK_OTP}</p>
            ) : (
              <p className="text-xs text-slate-500">Nếu chưa nhận được OTP, vui lòng liên hệ quản lý để gửi lại mã.</p>
            )}
          </div>
        )}

        {step === 3 && (
          <form onSubmit={handleSetPassword} className="space-y-5">
            <div className="space-y-2">
              <label htmlFor="new-password" className="text-sm font-medium text-slate-700">Mật khẩu mới</label>
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 focus-within:border-blue-500">
                <LockKeyhole className="h-4 w-4 text-slate-400" />
                <input
                  id="new-password"
                  type={showNewPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  className="w-full border-0 bg-transparent text-sm focus:outline-none"
                  autoComplete="new-password"
                />
                <button type="button" onClick={() => setShowNewPassword((current) => !current)} className="text-slate-500" aria-label={showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'}>
                  {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor="confirm-new-password" className="text-sm font-medium text-slate-700">Xác nhận mật khẩu mới</label>
              <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 focus-within:border-blue-500">
                <LockKeyhole className="h-4 w-4 text-slate-400" />
                <input
                  id="confirm-new-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  className="w-full border-0 bg-transparent text-sm focus:outline-none"
                  autoComplete="new-password"
                />
                <button type="button" onClick={() => setShowConfirmPassword((current) => !current)} className="text-slate-500" aria-label={showConfirmPassword ? 'Ẩn xác nhận mật khẩu' : 'Hiện xác nhận mật khẩu'}>
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <PasswordRequirements password={newPassword} currentPassword={temporaryPassword} />

            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
            {successMessage && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{successMessage}</div>}

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Đang kích hoạt...' : 'Hoàn tất kích hoạt'}
            </Button>

            {successMessage && successMessage.includes('Kích hoạt tài khoản thành công') && (
              <Button type="button" variant="outline" className="w-full" onClick={() => navigate('/login')}>
                Đến trang đăng nhập
              </Button>
            )}
          </form>
        )}
      </div>
    </div>
  )
}
