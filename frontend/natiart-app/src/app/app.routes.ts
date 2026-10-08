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
    path: 'forgot-password',
    loadComponent: () => import('./directory/components/auth/password-reset-request/password-reset-request.component')
      .then(m => m.PasswordResetRequestComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./directory/components/auth/password-reset/password-reset.component')
      .then(m => m.PasswordResetComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./product/components/customer/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'products',
    loadComponent: () => import('./product/components/customer/catalog/catalog.component').then(m => m.CatalogComponent)
  },
  {path: 'about', redirectTo: '/products', pathMatch: 'full'},
  {path: 'contact', redirectTo: '/products', pathMatch: 'full'},
  {
    path: 'account',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () => import('./product/components/customer/customer-profile/customer-profile.component')
      .then(m => m.CustomerProfileComponent),
    children: [
      {path: '', pathMatch: 'full', loadComponent: () => import('./product/components/customer/order-history/order-history.component').then(m => m.OrderHistoryComponent)},
      {path: 'profile', loadComponent: () => import('./directory/components/account/account-details.component').then(m => m.AccountDetailsComponent)},
      {path: 'security', loadComponent: () => import('./directory/components/account/change-password.component').then(m => m.ChangePasswordComponent)}
    ]
  },
  {path: 'faq', redirectTo: '/products', pathMatch: 'full'},
  {path: 'shipping-returns', redirectTo: '/products', pathMatch: 'full'},
  {
    path: 'care-instructions',
    data: {title: $localize`Care instructions`, message: $localize`Handle porcelain with clean, dry hands and avoid sudden temperature changes.`},
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
  {
    path: 'not-found',
    data: {title: $localize`Page not found`, message: $localize`The page you requested does not exist.`},
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
  {
    path: 'product/:id',
    canDeactivate: [productGuard],
    loadComponent: () => import('./product/components/customer/product-detail/product-detail.component').then(m => m.ProductDetailComponent)
  },
  {
    path: 'cart',
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
      {
        path: 'orders',
        loadComponent: () => import('./product/components/admin/admin-order-management/admin-order-management.component')
          .then(m => m.AdminOrderManagementComponent)
      },
      {path: '', redirectTo: 'categories', pathMatch: 'full'}
    ]
  },
  {path: '', redirectTo: '/dashboard', pathMatch: 'full'},
  {path: '**', redirectTo: '/not-found'}
];
