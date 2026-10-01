import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { logoutRequest, refreshAccessToken } from '../lib/api'
import type { User, TokenResponse } from '../types'

type JwtPayload = {
  sub?: string
  email?: string
  role?: User['role']
  exp?: number
}

type AuthSession = {
  token: string | null
  user: User | null
}

type AuthState = {
  session: AuthSession
  ready: boolean
}

type AuthContextValue = {
  user: User | null
  token: string | null
  isLoggedIn: boolean
  isAuthReady: boolean
  saveAuth: (auth: TokenResponse) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodeUser(token: string): User | null {
  try {
    const encodedPayload = token.split('.')[1]
    if (!encodedPayload) return null

    const normalized = encodedPayload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded)) as JwtPayload
    const id = Number(payload.sub)

    if (!Number.isSafeInteger(id) || !payload.email) return null
    if (payload.role !== 'USER' && payload.role !== 'SELLER' && payload.role !== 'ADMIN') return null
    if (payload.exp && payload.exp * 1000 <= Date.now()) return null

    return { id, email: payload.email, role: payload.role }
  } catch {
    return null
  }
}

function loadAuthState(): AuthState {
  const token = localStorage.getItem('token')
  const user = token ? decodeUser(token) : null

  if (!token) {
    return { session: { token: null, user: null }, ready: true }
  }

  if (!user) {
    return { session: { token: null, user: null }, ready: false }
  }

  return { session: { token, user }, ready: true }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>(loadAuthState)
  const { session } = authState

  const saveAuth = useCallback((auth: TokenResponse) => {
    const user = decodeUser(auth.accessToken)
    if (!user) throw new Error('로그인 응답의 토큰 정보가 올바르지 않습니다.')

    localStorage.setItem('token', auth.accessToken)
    setAuthState({ session: { token: auth.accessToken, user }, ready: true })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    setAuthState({ session: { token: null, user: null }, ready: true })
    // 화면 전환은 로컬 상태만으로 즉시 처리하고, 서버 쪽 refresh token 폐기·쿠키 삭제는 백그라운드로 시도한다.
    logoutRequest().catch(() => undefined)
  }, [])

  useEffect(() => {
    const handleExpired = () => logout()
    const handleRefreshed = (event: Event) => {
      saveAuth((event as CustomEvent<TokenResponse>).detail)
    }
    window.addEventListener('auth-expired', handleExpired)
    window.addEventListener('auth-refreshed', handleRefreshed)
    return () => {
      window.removeEventListener('auth-expired', handleExpired)
      window.removeEventListener('auth-refreshed', handleRefreshed)
    }
  }, [logout, saveAuth])

  useEffect(() => {
    if (authState.ready) return
    refreshAccessToken()
      .then(saveAuth)
      .catch(logout)
  }, [authState.ready, logout, saveAuth])

  const value = useMemo(() => ({
    user: session.user,
    token: session.token,
    isLoggedIn: Boolean(session.token && session.user),
    isAuthReady: authState.ready,
    saveAuth,
    logout
  }), [authState.ready, session, saveAuth, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
