export interface PoolCandidate {
  id: string
  fullName: string
  employeeCode: string
}

export const getEmployeePoolSelection = (candidates: PoolCandidate[], currentPoolIds: string[]) => {
  const available = new Set(candidates.map((candidate) => candidate.id))
  return currentPoolIds.filter((id) => available.has(id))
}

export const buildEmployeePoolPayload = (selectedIds: string[]) =>
  selectedIds.length ? JSON.stringify(selectedIds.map(Number)) : ''