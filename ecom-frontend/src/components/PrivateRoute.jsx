import { useSelector } from 'react-redux'
import { Navigate, Outlet, useLocation } from 'react-router-dom';

// Guards groups of routes in App.jsx:
//   <PrivateRoute publicPage />  login and register: only for people who aren't logged in
//   <PrivateRoute />             checkout etc.: needs any logged-in user
//   <PrivateRoute adminOnly />   the admin area: admins, and sellers for orders and products
const PrivateRoute = ({ publicPage = false, adminOnly = false }) => {
    const { user } = useSelector((state) => state.auth);
    const location = useLocation();
    const isAdmin = user?.roles?.includes("ROLE_ADMIN");
    const isSeller = user?.roles?.includes("ROLE_SELLER");

    // Login/register pages: once you're logged in, go where you were headed (or home)
    if (publicPage) {
        return user ? <Navigate to={location.state?.from || "/"} replace /> : <Outlet />;
    }

    // Everything else needs a login. Remember this page so the login page can send you back.
    if (!user) {
        return <Navigate to="/login" state={{ from: location.pathname }} replace />;
    }

    // The admin area: admins see everything, sellers only orders and products
    if (adminOnly) {
        if (!isAdmin && !isSeller) {
            return <Navigate to="/" replace />;
        }
        const sellerPaths = ["/admin/orders", "/admin/products"];
        if (!isAdmin && !sellerPaths.some((path) => location.pathname.startsWith(path))) {
            return <Navigate to="/" replace />;
        }
    }

    return <Outlet />;
};

export default PrivateRoute
