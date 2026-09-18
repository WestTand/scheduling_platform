import { shiftTypeSeed } from '@/mocks/managementData'
import { getWorkMinutes } from '@/configs/shiftDefinitions'
import type { PaginatedResult, ShiftType, ShiftTypeFormValues } from '@/types/management'

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

let shiftTypes: ShiftType[] = clone(shiftTypeSeed)

export const shiftTypeService = {
  async list({ page = 1, pageSize = 5, search = '', status = 'ALL' }: { page?: number; pageSize?: number; search?: string; status?: string }) {
    await delay(300)

    const filtered = shiftTypes.filter((shiftType) => {
      const matchesSearch = !search || [shiftType.code, shiftType.name].some((value) => value.toLowerCase().includes(search.toLowerCase()))
      const matchesStatus = status === 'ALL' || shiftType.status === status
      return matchesSearch && matchesStatus
    })

    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / pageSize))
    const safePage = Math.min(page, totalPages)
    const startIndex = (safePage - 1) * pageSize

    return {
      data: filtered.slice(startIndex, startIndex + pageSize),
      page: safePage,
      pageSize,
      total,
      totalPages,
    } satisfies PaginatedResult<ShiftType>
  },

  async getById(id: string) {
    await delay(200)
    return shiftTypes.find((shiftType) => shiftType.id === id) ?? null
  },

  async add(data: ShiftTypeFormValues) {
    await delay(350)
    const exists = shiftTypes.some((shiftType) => shiftType.code.toLowerCase() === data.code.trim().toLowerCase())

    if (exists) {
      throw new Error('Mã ca trực đã tồn tại trong hệ thống.')
    }

    const newShiftType: ShiftType = {
      id: `shift-${Date.now()}`,
      code: data.code.trim(),
      name: data.name.trim(),
      group: data.group,
      startTime: data.startTime,
      endTime: data.endTime,
      crossesMidnight: data.crossesMidnight,
      breakStart: data.breakStart,
      breakEnd: data.breakEnd,
      breakMinutes: data.breakMinutes,
      workMinutes: data.workMinutes || getWorkMinutes(data.startTime, data.endTime),
      applicableDayType: data.applicableDayType,
      color: data.color,
      description: data.description.trim(),
      status: data.status,
    }

    shiftTypes = [newShiftType, ...shiftTypes]
    return newShiftType
  },

  async update(id: string, data: ShiftTypeFormValues) {
    await delay(300)
    const index = shiftTypes.findIndex((shiftType) => shiftType.id === id)

    if (index === -1) {
      throw new Error('Loại ca trực không tồn tại.')
    }

    const duplicate = shiftTypes.some(
      (shiftType) => shiftType.id !== id && shiftType.code.toLowerCase() === data.code.trim().toLowerCase(),
    )

    if (duplicate) {
      throw new Error('Mã ca trực đã tồn tại trong hệ thống.')
    }

    shiftTypes[index] = {
      ...shiftTypes[index],
      code: data.code.trim(),
      name: data.name.trim(),
      group: data.group,
      startTime: data.startTime,
      endTime: data.endTime,
      crossesMidnight: data.crossesMidnight,
      breakStart: data.breakStart,
      breakEnd: data.breakEnd,
      breakMinutes: data.breakMinutes,
      workMinutes: data.workMinutes || getWorkMinutes(data.startTime, data.endTime),
      applicableDayType: data.applicableDayType,
      color: data.color,
      description: data.description.trim(),
      status: data.status,
    }

    return shiftTypes[index]
  },

  async toggleStatus(id: string) {
    await delay(250)
    const shiftType = shiftTypes.find((item) => item.id === id)

    if (!shiftType) {
      throw new Error('Loại ca trực không tồn tại.')
    }

    shiftType.status = shiftType.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    return shiftType
  },

  async remove(id: string) {
    await delay(250)

    const current = shiftTypes.find((item) => item.id === id)
    if (!current) {
      throw new Error('Loại ca trực không tồn tại.')
    }

    const isInUse = current.code === 'THONG_TAM' || current.code === 'NGOAI_GIO_1' || current.code === 'NGOAI_GIO_2' || current.code === 'TRUC_SANG' || current.code === 'TRUC_CHIEU' || current.code === 'TRUC_DEM'
    if (isInUse) {
      throw new Error('Loại ca này đang được dùng trong mock schedule, không thể xóa ngay. Hãy tạm ngưng hoặc thay đổi lịch sử dụng.')
    }

    shiftTypes = shiftTypes.filter((item) => item.id !== id)
    return current
  },

  reset() {
    shiftTypes = clone(shiftTypeSeed)
  },
}

