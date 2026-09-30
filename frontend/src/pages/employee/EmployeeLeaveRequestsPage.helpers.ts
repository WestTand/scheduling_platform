const DUPLICATE_LEAVE_HINTS = [
  'yêu cầu nghỉ chờ duyệt hoặc đã duyệt',
  'ngày nghỉ đã duyệt trùng',
]

export const getLeaveSubmitErrorDialogMessage = (message: string) => {
  const normalized = message.trim()

  if (!normalized) {
    return 'Không thể tạo yêu cầu nghỉ. Vui lòng thử lại.'
  }

  return DUPLICATE_LEAVE_HINTS.some((hint) => normalized.toLowerCase().includes(hint))
    ? normalized
    : normalized
}
