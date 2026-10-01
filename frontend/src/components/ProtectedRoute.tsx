import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { User } from '../types'
import { Loading } from './Loading'

export function ProtectedRoute({ children, role }: { children: React.ReactNode; role?: User['role'] }) {
  const { isLoggedIn, isAuthReady, user } = useAuth()
  const location = useLocation()
  if (!isAuthReady) return <Loading />
  if (!isLoggedIn) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (role && user?.role !== role) return <Navigate to="/" replace />
  return children
}
