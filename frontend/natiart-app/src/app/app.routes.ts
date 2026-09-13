import {Routes} from '@angular/router';
import {authGuard} from "./directory/guards/auth.guard";
import {adminGuard} from "./directory/guards/admin.guard";
import {productGuard} from "./product/guards/product-guard.guard";

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./directory/components/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'logout',
    loadComponent: () => import('./directory/components/auth/logout/logout.component').then(m => m.LogoutComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./directory/components/auth/signup/signup.component').then(m => m.SignupComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./product/components/customer/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'product/:id',
    canActivate: [authGuard],
    canDeactivate: [productGuard],
    loadComponent: () => import('./product/components/customer/product-detail/product-detail.component').then(m => m.ProductDetailComponent)
  },
  {
    path: 'cart',
    canActivate: [authGuard],
    loadComponent: () => import('./product/components/customer/cart/cart.component').then(m => m.CartComponent)
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadComponent: () => import('./product/components/customer/checkout/checkout.component').then(m => m.CheckoutComponent)
  },
  {
    path: 'pix-payment/:paymentId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./product/components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component')
        .then(m => m.PixPaymentConfirmationComponent)
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./product/components/admin/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent),
    children: [
      {
        path: 'dashboard',
        redirectTo: 'categories',
        pathMatch: 'full'
      },
      {
        path: 'categories',
        loadComponent: () => import('./product/components/admin/admin-category-management/admin-category-management.component')
          .then(m => m.CategoryManagementComponent)
      },
      {
        path: 'products',
        loadComponent: () => import('./product/components/admin/admin-product-management/admin-product-management.component')
          .then(m => m.ProductManagementComponent)
      },
      {
        path: 'packages',
        loadComponent: () => import('./product/components/admin/admin-package-management/admin-package-management.component')
          .then(m => m.PackageManagementComponent)
      },
      {path: '', redirectTo: 'categories', pathMatch: 'full'}
    ]
  },
  {path: '', redirectTo: '/dashboard', pathMatch: 'full'},
  {path: '**', redirectTo: '/dashboard'}
];
