import { useId } from 'react'

interface OtpInputProps {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  length?: number
}

export function OtpInput({ value, onChange, disabled = false, length = 6 }: OtpInputProps) {
  const inputId = useId()
  const digits = Array.from({ length }, (_, index) => value[index] ?? '')

  const handleChange = (index: number, nextValue: string) => {
    const sanitized = nextValue.replace(/\D/g, '').slice(0, 1)
    const nextDigits = digits.map((digit, digitIndex) => (digitIndex === index ? sanitized : digit))
    const nextOtp = nextDigits.join('')
    onChange(nextOtp)

    if (sanitized && index < length - 1) {
      const nextInput = document.getElementById(`${inputId}-${index + 1}`) as HTMLInputElement | null
      nextInput?.focus()
    }
  }

  return (
    <div className="flex items-center justify-center gap-2">
      {Array.from({ length }, (_, index) => (
        <input
          key={index}
          id={`${inputId}-${index}`}
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          value={digits[index]}
          onChange={(event) => handleChange(index, event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Backspace' && !digits[index] && index > 0) {
              const previousInput = document.getElementById(`${inputId}-${index - 1}`) as HTMLInputElement | null
              previousInput?.focus()
            }
          }}
          disabled={disabled}
          maxLength={1}
          className="h-12 w-12 rounded-xl border border-slate-200 bg-slate-50 text-center text-lg font-semibold text-slate-800 focus:border-blue-500 focus:outline-none disabled:cursor-not-allowed disabled:opacity-60"
          aria-label={`Chữ số OTP ${index + 1}`}
        />
      ))}
    </div>
  )
}
