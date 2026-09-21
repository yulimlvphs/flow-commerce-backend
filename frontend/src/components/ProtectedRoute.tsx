import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Loading } from './Loading'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, isAuthReady } = useAuth()
  const location = useLocation()
  if (!isAuthReady) return <Loading />
  if (!isLoggedIn) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return children
}
