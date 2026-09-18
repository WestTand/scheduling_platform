interface PasswordRequirementsProps {
  password: string
  currentPassword?: string
}

export function PasswordRequirements({ password, currentPassword }: PasswordRequirementsProps) {
  const checks = [
    { label: 'Tối thiểu 8 ký tự', valid: password.length >= 8 },
    { label: 'Có chữ hoa', valid: /[A-Z]/.test(password) },
    { label: 'Có chữ thường', valid: /[a-z]/.test(password) },
    { label: 'Có chữ số', valid: /\d/.test(password) },
    {
      label: 'Khác mật khẩu tạm thời',
      valid: !currentPassword || !password || password !== currentPassword,
    },
  ]

  return (
    <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50 p-3">
      <p className="text-sm font-medium text-slate-700">Yêu cầu mật khẩu</p>
      <ul className="space-y-1 text-sm text-slate-600">
        {checks.map((check) => (
          <li key={check.label} className={`flex items-center gap-2 ${check.valid ? 'text-emerald-700' : 'text-slate-500'}`}>
            <span className={`inline-block h-2 w-2 rounded-full ${check.valid ? 'bg-emerald-600' : 'bg-slate-300'}`} />
            {check.label}
          </li>
        ))}
      </ul>
    </div>
  )
}
