export const validateExportSelection = (periodId: string): string | null => {
  if (!periodId) {
    return 'Vui lòng chọn kỳ lịch để xuất file.'
  }
  return null
}
