import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { DealDetailPage } from './pages/DealDetailPage'
import { SellerProductsPage } from './pages/SellerProductsPage'
import { SellerApplicationPage } from './pages/SellerApplicationPage'
import { AdminSellerApplicationsPage } from './pages/AdminSellerApplicationsPage'
import { DealEditorPage } from './pages/DealEditorPage'
import { SignupPage } from './pages/SignupPage'

export default function App() {
  return <Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="deals/:id" element={<DealDetailPage />} />
      <Route path="seller/deals" element={<ProtectedRoute roles={['SELLER', 'ADMIN']}><HomePage management /></ProtectedRoute>} />
      <Route path="seller/deals/new" element={<ProtectedRoute roles={['SELLER', 'ADMIN']}><DealEditorPage /></ProtectedRoute>} />
      <Route path="seller/deals/:id/edit" element={<ProtectedRoute roles={['SELLER', 'ADMIN']}><DealEditorPage /></ProtectedRoute>} />
      <Route path="seller/products" element={<ProtectedRoute role="SELLER"><SellerProductsPage /></ProtectedRoute>} />
      <Route path="seller/apply" element={<ProtectedRoute><SellerApplicationPage /></ProtectedRoute>} />
      <Route path="seller/apply/me" element={<ProtectedRoute><SellerApplicationPage showStatus /></ProtectedRoute>} />
      <Route path="admin/seller-applications" element={<ProtectedRoute role="ADMIN"><AdminSellerApplicationsPage /></ProtectedRoute>} />
      <Route path="login" element={<LoginPage />} />
      <Route path="signup" element={<SignupPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Route>
  </Routes>
}
