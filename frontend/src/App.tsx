import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { DealDetailPage } from './pages/DealDetailPage'
import { SellerProductsPage } from './pages/SellerProductsPage'
import { SignupPage } from './pages/SignupPage'

export default function App() {
  return <Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="deals/:id" element={<DealDetailPage />} />
      <Route path="seller/products" element={<ProtectedRoute role="SELLER"><SellerProductsPage /></ProtectedRoute>} />
      <Route path="login" element={<LoginPage />} />
      <Route path="signup" element={<SignupPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Route>
  </Routes>
}
