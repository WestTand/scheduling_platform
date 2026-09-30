import { apiConfig } from '@/config/api'
import { clearStoredAuth, getStoredToken, updateStoredToken } from '@/services/authStorage'

const DEFAULT_ERROR_MESSAGE = 'Không thể kết nối máy chủ. Vui lòng thử lại.'

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  auth?: boolean
}

interface RefreshResponse {
  accessToken?: unknown
}

let refreshInFlight: Promise<string | null> | null = null

const buildUrl = (path: string) => {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    throw new ApiError(DEFAULT_ERROR_MESSAGE, 0, 'INVALID_API_PATH')
  }

  return `${apiConfig.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

const parseResponseBody = async (response: Response) => {
  const text = await response.text()

  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

const errorMessage = (body: unknown) => {
  if (typeof body === 'string' && body.trim()) {
    return body
  }

  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }

  if (body && typeof body === 'object' && 'detail' in body) {
    const detail = (body as { detail?: unknown }).detail
    if (typeof detail === 'string' && detail.trim()) {
      return detail
    }
  }

  return DEFAULT_ERROR_MESSAGE
}

const errorCode = (body: unknown) => {
  if (body && typeof body === 'object' && 'code' in body) {
    const code = (body as { code?: unknown }).code
    return typeof code === 'string' ? code : undefined
  }

  return undefined
}

const refreshAccessToken = async () => {
  const response = await fetch(buildUrl('/api/auth/refresh'), {
    method: 'POST',
    credentials: 'include',
  })

  if (!response.ok) {
    return null
  }

  const body = await parseResponseBody(response) as RefreshResponse
  return typeof body?.accessToken === 'string' && body.accessToken.trim() ? body.accessToken : null
}

const getFreshAccessToken = () => {
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null
    })
  }

  return refreshInFlight
}

const sendRequest = async (path: string, options: RequestOptions, accessToken: string | null) => {
  const headers = new Headers(options.headers)
  const hasBody = options.body !== undefined

  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.auth !== false && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  return fetch(buildUrl(path), {
    ...options,
    headers,
    credentials: options.credentials ?? 'include',
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })
}

const fetchWithAuthRetry = async (path: string, options: RequestOptions): Promise<Response> => {
  let response = await sendRequest(path, options, options.auth === false ? null : getStoredToken())

  if (!response.ok && response.status === 401 && options.auth !== false) {
    const refreshedToken = await getFreshAccessToken()

    if (refreshedToken) {
      updateStoredToken(refreshedToken)
      response = await sendRequest(path, options, refreshedToken)
    } else {
      clearStoredAuth()
    }
  }

  return response
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetchWithAuthRetry(path, options)
  const body = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return body as T
}

const parseAttachmentFilename = (header: string | null): string | null => {
  if (!header) {
    return null
  }
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return utf8[1]
    }
  }
  const quoted = /filename="([^"]+)"/i.exec(header)
  return quoted?.[1] ?? null
}

export async function requestBlob(path: string, options: RequestOptions = {}): Promise<{ blob: Blob; filename: string | null }> {
  const response = await fetchWithAuthRetry(path, options)

  if (!response.ok) {
    const body = await parseResponseBody(response)
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return { blob: await response.blob(), filename: parseAttachmentFilename(response.headers.get('Content-Disposition')) }
}

export async function requestVoid(path: string, options: RequestOptions = {}): Promise<void> {
  await requestJson<unknown>(path, options)
}
