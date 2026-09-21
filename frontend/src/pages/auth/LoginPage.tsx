import { Eye, EyeOff, LockKeyhole, Mail, ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/contexts/useAuth'
import { BackendLoginStateError } from '@/services/backendAuthService'
import type { LoginCredentials } from '@/types/auth'

const demoAccounts = [
  { role: 'Quản lý', email: 'manager@schedule.local', password: '123456' },
  { role: 'Nhân viên', email: 'employee@schedule.local', password: '123456' },
]

export function LoginPage() {
  const { login, isLoading } = useAuth()
  const navigate = useNavigate()
  const [credentials, setCredentials] = useState<LoginCredentials>({ email: '', password: '' })
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [touched, setTouched] = useState({ email: false, password: false })

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const emailEmpty = !credentials.email.trim()
    const passwordEmpty = !credentials.password

    setTouched({ email: true, password: true })

    if (emailEmpty || passwordEmpty) {
      setError('Vui lòng nhập email hoặc tên đăng nhập và mật khẩu để đăng nhập.')
      return
    }

    setError('')

    try {
      await login(credentials, rememberMe)
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
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-xl lg:grid-cols-[1.1fr_0.9fr]">
        <div className="hidden bg-gradient-to-br from-blue-700 via-blue-600 to-sky-500 p-10 text-white lg:flex lg:flex-col lg:justify-between">
          <div>
            <div className="mb-6 inline-flex items-center gap-3 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 backdrop-blur-sm">
              <ShieldCheck className="h-4 w-4" />
              Scheduling Platform
            </div>
            <h1 className="max-w-md text-4xl font-semibold leading-tight">Hệ thống phân công lịch làm việc</h1>
          </div>

          <div className="space-y-4">
            <div className="rounded-2xl border border-white/15 bg-white/5 p-4 backdrop-blur-sm">
              <p className="text-sm text-blue-100">Tài khoản dùng để kiểm thử giao diện</p>
              <div className="mt-3 space-y-2 text-sm">
                {demoAccounts.map((account) => (
                  <div key={account.email} className="rounded-xl bg-white/5 px-3 py-2">
                    <p className="font-medium">{account.role}</p>
                    <p className="text-blue-100">{account.email} / {account.password}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-center p-6 sm:p-10">
          <Card className="w-full max-w-md border-0 shadow-none">
            <CardHeader className="space-y-2 p-0">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-100 text-blue-700">
                <LockKeyhole className="h-7 w-7" />
              </div>
              <CardTitle className="text-2xl text-slate-900">Đăng nhập</CardTitle>
              <CardDescription>Quản lý lịch làm việc hiệu quả hơn với trải nghiệm trực quan.</CardDescription>
            </CardHeader>

            <CardContent className="p-0 pt-6">
              <form className="space-y-5" onSubmit={handleSubmit} noValidate>
                <div className="space-y-2">
                  <label htmlFor="email" className="text-sm font-medium text-slate-700">
                    Email hoặc tên đăng nhập
                  </label>
                  <div className={`flex items-center gap-2 rounded-xl border bg-slate-50 px-3 py-2 transition ${touched.email && !credentials.email.trim() ? 'border-red-300' : 'border-slate-200 focus-within:border-blue-500'}`}>
                    <Mail className="h-4 w-4 text-slate-400" />
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
                  </div>
                  {touched.email && !credentials.email.trim() && (
                    <p className="text-xs text-red-600">Vui lòng nhập email hoặc tên đăng nhập.</p>
                  )}
                </div>

                <div className="space-y-2">
                  <label htmlFor="password" className="text-sm font-medium text-slate-700">
                    Mật khẩu
                  </label>
                  <div className={`flex items-center gap-2 rounded-xl border bg-slate-50 px-3 py-2 transition ${touched.password && !credentials.password ? 'border-red-300' : 'border-slate-200 focus-within:border-blue-500'}`}>
                    <LockKeyhole className="h-4 w-4 text-slate-400" />
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={credentials.password}
                      onChange={(event) => {
                        setCredentials((current) => ({ ...current, password: event.target.value }))
                        if (error) setError('')
                      }}
                      onBlur={() => setTouched((current) => ({ ...current, password: true }))}
                      placeholder="••••••"
                      className="w-full border-0 bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      className="text-slate-500 transition hover:text-slate-700"
                      onClick={() => setShowPassword((current) => !current)}
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {touched.password && !credentials.password && (
                    <p className="text-xs text-red-600">Vui lòng nhập mật khẩu.</p>
                  )}
                </div>

                <div className="flex items-center justify-between gap-3 text-sm">
                  <label className="flex items-center gap-2 text-slate-600">
                    <input
                      type="checkbox"
                      checked={rememberMe}
                      onChange={(event) => setRememberMe(event.target.checked)}
                      className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                    />
                    Ghi nhớ đăng nhập
                  </label>
                </div>

                {error && (
                  <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                    {error}
                  </div>
                )}

                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading ? 'Đang đăng nhập...' : 'Đăng nhập'}
                </Button>

                <Button type="button" variant="outline" className="w-full" onClick={() => navigate('/activate-account')}>
                  Kích hoạt tài khoản
                </Button>

                {error && error.includes('Tài khoản chưa được kích hoạt.') && (
                  <div className="text-center text-sm">
                    <Link to={`/activate-account?username=${encodeURIComponent(credentials.email.trim())}`} className="font-medium text-blue-700 hover:text-blue-800">
                      Kích hoạt ngay
                    </Link>
                  </div>
                )}
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
