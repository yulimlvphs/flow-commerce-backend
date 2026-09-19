import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { CartPage } from './pages/CartPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OrderCompletePage } from './pages/OrderCompletePage'
import { OrderDetailPage } from './pages/OrderDetailPage'
import { OrdersPage } from './pages/OrdersPage'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { SignupPage } from './pages/SignupPage'

const protectedPage = (page: React.ReactNode) => <ProtectedRoute>{page}</ProtectedRoute>

export default function App() {
  return <Routes>
    <Route element={<Layout />}>
      <Route index element={<HomePage />} />
      <Route path="products/:id" element={<ProductDetailPage />} />
      <Route path="login" element={<LoginPage />} />
      <Route path="signup" element={<SignupPage />} />
      <Route path="cart" element={protectedPage(<CartPage />)} />
      <Route path="orders" element={protectedPage(<OrdersPage />)} />
      <Route path="orders/complete" element={protectedPage(<OrderCompletePage />)} />
      <Route path="orders/:orderId" element={protectedPage(<OrderDetailPage />)} />
      <Route path="checkout" element={protectedPage(<CheckoutPage />)} />
      <Route path="*" element={<NotFoundPage />} />
    </Route>
  </Routes>
}
