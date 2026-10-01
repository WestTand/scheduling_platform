import { getShiftDefinitionsForDate, shiftDefinitions } from '@/configs/shiftDefinitions'
import type { ShiftCode } from '@/configs/shiftDefinitions'

const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export const isPreferenceDateInRange = (preferenceDate: string, from: string, to: string) =>
  (!from || preferenceDate >= from) && (!to || preferenceDate <= to)

export const isDateRangeInverted = (from: string, to: string) => Boolean(from) && Boolean(to) && from > to

export const isPreferenceDateValid = (value: string) => {
  if (!DATE_ONLY_PATTERN.test(value)) {
    return false
  }

  const [year, month, day] = value.split('-').map(Number)
  const parsed = new Date(year, month - 1, day)
  return parsed.getFullYear() === year && parsed.getMonth() === month - 1 && parsed.getDate() === day
}

export const getSelectableShiftCodes = (preferenceDate: string): ShiftCode[] => {
  const definitions = isPreferenceDateValid(preferenceDate) ? getShiftDefinitionsForDate(preferenceDate) : shiftDefinitions
  return definitions.map((definition) => definition.code)
}

export const resolveSelectedShiftCode = (preferenceDate: string, currentCode: string): string => {
  const selectable = getSelectableShiftCodes(preferenceDate)
  if (currentCode && selectable.includes(currentCode as ShiftCode)) {
    return currentCode
  }

  return selectable[0] ?? ''
}

export const normalizePriority = (value: string): number | null => {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }

  const parsed = Number(trimmed)
  return Number.isInteger(parsed) ? parsed : null
}

export const getTodayDateInputValue = (now: Date = new Date()) => {
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export interface PreferenceFormValues {
  preferenceDate: string
  relatedShiftCode: string
  priority: string
  note: string
}

export const validatePreferenceForm = ({ preferenceDate, relatedShiftCode, priority, note }: PreferenceFormValues) => {
  if (!isPreferenceDateValid(preferenceDate)) {
    return 'Vui lòng chọn ngày áp dụng cho nguyện vọng.'
  }

  if (!relatedShiftCode || !getSelectableShiftCodes(preferenceDate).includes(relatedShiftCode as ShiftCode)) {
    return 'Vui lòng chọn loại ca liên quan.'
  }

  if (!note.trim()) {
    return 'Nội dung ghi chú không được bỏ trống.'
  }

  const normalizedPriority = normalizePriority(priority)
  if (normalizedPriority === null || normalizedPriority < 1 || normalizedPriority > 5) {
    return 'Mức ưu tiên phải từ 1 đến 5.'
  }

  return ''
}