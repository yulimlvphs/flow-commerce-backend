import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
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

type AuthContextValue = {
  member: Member | null
  token: string | null
  isLoggedIn: boolean
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

function loadSession(): AuthSession {
  const token = localStorage.getItem('token')
  const member = token ? decodeMember(token) : null

  if (!token || !member) {
    localStorage.removeItem('token')
    localStorage.removeItem('member')
    return { token: null, member: null }
  }

  // 이전 프론트엔드에서 저장하던 회원 캐시는 JWT 클레임으로 대체한다.
  localStorage.removeItem('member')
  return { token, member }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession>(loadSession)

  const saveAuth = useCallback((auth: TokenResponse) => {
    const member = decodeMember(auth.accessToken)
    if (!member) throw new Error('로그인 응답의 토큰 정보가 올바르지 않습니다.')

    localStorage.setItem('token', auth.accessToken)
    localStorage.removeItem('member')
    setSession({ token: auth.accessToken, member })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('member')
    setSession({ token: null, member: null })
  }, [])

  useEffect(() => {
    const handleExpired = () => logout()
    window.addEventListener('auth-expired', handleExpired)
    return () => window.removeEventListener('auth-expired', handleExpired)
  }, [logout])

  const value = useMemo(() => ({
    member: session.member,
    token: session.token,
    isLoggedIn: Boolean(session.token && session.member),
    saveAuth,
    logout
  }), [session, saveAuth, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
