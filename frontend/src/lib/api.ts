type ApiEnvelope<T> = {
  code: string
  data?: T
  message?: string
}

export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const token = localStorage.getItem('token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(url, { ...options, headers })

  if ((response.status === 401 || response.status === 403) && !url.startsWith('/api/auth/')) {
    localStorage.removeItem('token')
    localStorage.removeItem('member')
    window.dispatchEvent(new CustomEvent('auth-expired'))
    throw new Error('인증이 만료되었습니다')
  }

  const text = await response.text()
  let payload: ApiEnvelope<T> | undefined
  try {
    payload = text ? (JSON.parse(text) as ApiEnvelope<T>) : undefined
  } catch {
    throw new Error(`서버 응답을 처리할 수 없습니다. (HTTP ${response.status})`)
  }

  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`)
  }

  if (payload?.code && payload.code !== 'SUCCESS') {
    throw new Error(payload.message || payload.code)
  }

  return payload?.data as T
}
