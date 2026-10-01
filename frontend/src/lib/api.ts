import type { TokenResponse } from '../types'

type ApiEnvelope<T> = {
  code: string
  data?: T
  message?: string
}

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly code?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

let refreshRequest: Promise<TokenResponse> | null = null

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  let payload: ApiEnvelope<T> | undefined

  try {
    payload = text ? (JSON.parse(text) as ApiEnvelope<T>) : undefined
  } catch {
    throw new Error(`서버 응답을 처리할 수 없습니다. (HTTP ${response.status})`)
  }

  if (!response.ok) {
    throw new ApiError(payload?.message || `HTTP ${response.status}`, response.status, payload?.code)
  }

  if (payload?.code && payload.code !== 'SUCCESS') {
    throw new ApiError(payload.message || payload.code, response.status, payload.code)
  }

  return payload?.data as T
}

function send(url: string, options: RequestInit, accessToken: string | null) {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  else headers.delete('Authorization')

  return fetch(url, {
    ...options,
    headers,
    credentials: options.credentials ?? 'include'
  })
}

export function refreshAccessToken(): Promise<TokenResponse> {
  if (!refreshRequest) {
    refreshRequest = send('/api/auth/refresh', { method: 'POST' }, null)
      .then((response) => parseResponse<TokenResponse>(response))
      .finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

export async function logoutRequest(): Promise<void> {
  const response = await send('/api/auth/logout', { method: 'POST' }, null)
  await parseResponse<void>(response)
}

function saveRefreshedAuth(auth: TokenResponse) {
  localStorage.setItem('token', auth.accessToken)
  window.dispatchEvent(new CustomEvent<TokenResponse>('auth-refreshed', { detail: auth }))
}

function expireAuth() {
  localStorage.removeItem('token')
  window.dispatchEvent(new CustomEvent('auth-expired'))
}

export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const isAuthEndpoint = url.startsWith('/api/auth/')
  const requestToken = isAuthEndpoint ? null : localStorage.getItem('token')
  let response = await send(url, options, requestToken)

  if (response.status === 401 && !isAuthEndpoint) {
    try {
      const currentToken = localStorage.getItem('token')
      let retryToken = currentToken

      if (!currentToken || currentToken === requestToken) {
        const refreshedAuth = await refreshAccessToken()
        saveRefreshedAuth(refreshedAuth)
        retryToken = refreshedAuth.accessToken
      }

      response = await send(url, options, retryToken)
    } catch {
      expireAuth()
      throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.')
    }

    if (response.status === 401) {
      expireAuth()
      throw new Error('인증이 만료되었습니다. 다시 로그인해주세요.')
    }
  }

  return parseResponse<T>(response)
}
