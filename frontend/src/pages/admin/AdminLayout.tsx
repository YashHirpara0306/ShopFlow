import { NavLink, Outlet } from "react-router-dom";

export function AdminLayout() {
  return (
    <div className="container page">
      <div className="page__head">
        <div className="crumbs">Admin</div>
        <h1>Store admin</h1>
      </div>
      <nav className="adminbar">
        <NavLink to="/admin/products" className={({ isActive }) => (isActive ? "is-active" : "")}>
          Products
        </NavLink>
        <NavLink to="/admin/inventory" className={({ isActive }) => (isActive ? "is-active" : "")}>
          Inventory
        </NavLink>
      </nav>
      <Outlet />
    </div>
  );
}
