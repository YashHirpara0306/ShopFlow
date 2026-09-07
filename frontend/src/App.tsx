import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { RequireAdmin, RequireAuth } from "./components/Guards";
import { Home } from "./pages/Home";
import { Products } from "./pages/Products";
import { ProductDetail } from "./pages/ProductDetail";
import { Cart } from "./pages/Cart";
import { Checkout } from "./pages/Checkout";
import { OrderStatus } from "./pages/OrderStatus";
import { Orders } from "./pages/Orders";
import { TrackOrder } from "./pages/TrackOrder";
import { Login } from "./pages/Login";
import { Register } from "./pages/Register";
import { Account } from "./pages/Account";
import { NotFound } from "./pages/NotFound";
import { AdminLayout } from "./pages/admin/AdminLayout";
import { AdminProducts } from "./pages/admin/AdminProducts";
import { AdminInventory } from "./pages/admin/AdminInventory";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="products" element={<Products />} />
        <Route path="products/:id" element={<ProductDetail />} />
        <Route path="cart" element={<Cart />} />
        <Route path="track" element={<TrackOrder />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />

        <Route
          path="checkout"
          element={
            <RequireAuth>
              <Checkout />
            </RequireAuth>
          }
        />
        <Route
          path="orders"
          element={
            <RequireAuth>
              <Orders />
            </RequireAuth>
          }
        />
        <Route
          path="orders/:id"
          element={
            <RequireAuth>
              <OrderStatus />
            </RequireAuth>
          }
        />
        <Route
          path="account"
          element={
            <RequireAuth>
              <Account />
            </RequireAuth>
          }
        />

        <Route
          path="admin"
          element={
            <RequireAdmin>
              <AdminLayout />
            </RequireAdmin>
          }
        >
          <Route index element={<AdminProducts />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="inventory" element={<AdminInventory />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
