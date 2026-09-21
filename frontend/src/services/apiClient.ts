import { apiConfig } from '@/config/api'
import { getStoredToken } from '@/services/authStorage'

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

  return DEFAULT_ERROR_MESSAGE
}

const errorCode = (body: unknown) => {
  if (body && typeof body === 'object' && 'code' in body) {
    const code = (body as { code?: unknown }).code
    return typeof code === 'string' ? code : undefined
  }

  return undefined
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const hasBody = options.body !== undefined

  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.auth !== false) {
    const token = getStoredToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const response = await fetch(buildUrl(path), {
    ...options,
    headers,
    credentials: options.credentials ?? 'include',
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })

  const body = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(errorMessage(body), response.status, errorCode(body))
  }

  return body as T
}

export async function requestVoid(path: string, options: RequestOptions = {}): Promise<void> {
  await requestJson<unknown>(path, options)
}
