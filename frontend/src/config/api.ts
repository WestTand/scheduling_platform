const DEFAULT_API_BASE_URL = 'http://localhost:8080'

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

export const apiConfig = {
  apiBaseUrl: trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL),
  useMockApi: import.meta.env.VITE_USE_MOCK_API !== 'false',
}
