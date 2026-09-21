import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { logoutRequest, refreshAccessToken } from '../lib/api'
import type { Member, TokenResponse } from '../types'

type JwtPayload = {
  sub?: string
  email?: string
  role?: Member['role']
  exp?: number
}

type AuthSession = {
  token: string | null
  member: Member | null
}

type AuthState = {
  session: AuthSession
  ready: boolean
}

type AuthContextValue = {
  member: Member | null
  token: string | null
  isLoggedIn: boolean
  isAuthReady: boolean
  saveAuth: (auth: TokenResponse) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodeMember(token: string): Member | null {
  try {
    const encodedPayload = token.split('.')[1]
    if (!encodedPayload) return null

    const normalized = encodedPayload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded)) as JwtPayload
    const id = Number(payload.sub)

    if (!Number.isSafeInteger(id) || !payload.email) return null
    if (payload.role !== 'USER' && payload.role !== 'ADMIN') return null
    if (payload.exp && payload.exp * 1000 <= Date.now()) return null

    return { id, email: payload.email, role: payload.role }
  } catch {
    return null
  }
}

function loadAuthState(): AuthState {
  const token = localStorage.getItem('token')
  const member = token ? decodeMember(token) : null

  if (!token) {
    localStorage.removeItem('member')
    return { session: { token: null, member: null }, ready: true }
  }

  if (!member) {
    return { session: { token: null, member: null }, ready: false }
  }

  // 이전 프론트엔드에서 저장하던 회원 캐시는 JWT 클레임으로 대체한다.
  localStorage.removeItem('member')
  return { session: { token, member }, ready: true }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>(loadAuthState)
  const { session } = authState

  const saveAuth = useCallback((auth: TokenResponse) => {
    const member = decodeMember(auth.accessToken)
    if (!member) throw new Error('로그인 응답의 토큰 정보가 올바르지 않습니다.')

    localStorage.setItem('token', auth.accessToken)
    localStorage.removeItem('member')
    setAuthState({ session: { token: auth.accessToken, member }, ready: true })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('member')
    setAuthState({ session: { token: null, member: null }, ready: true })
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
    member: session.member,
    token: session.token,
    isLoggedIn: Boolean(session.token && session.member),
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
