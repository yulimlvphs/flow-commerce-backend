import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { Member } from '../types'
import { Loading } from './Loading'

export function ProtectedRoute({ children, role }: { children: React.ReactNode; role?: Member['role'] }) {
  const { isLoggedIn, isAuthReady, member } = useAuth()
  const location = useLocation()
  if (!isAuthReady) return <Loading />
  if (!isLoggedIn) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (role && member?.role !== role) return <Navigate to="/" replace />
  return children
}
