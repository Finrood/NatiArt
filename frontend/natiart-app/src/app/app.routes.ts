import {Routes} from '@angular/router';
import {authGuard} from "./directory/guards/auth.guard";
import {adminGuard} from "./directory/guards/admin.guard";
import {productGuard} from "./product/guards/product-guard.guard";

export const routes: Routes = [
  {path: 'products', loadComponent: () => import('./product/components/customer/catalog/catalog.component').then(m => m.CatalogComponent)},
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
    path: 'products',
    loadComponent: () => import('./product/components/customer/catalog/catalog.component').then(m => m.CatalogComponent)
  },
  {
    path: 'about',
    data: {
      title: $localize`About us`,
      message: $localize`Handmade pieces for your home.`
    },
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
  {
    path: 'contact',
    data: {
      title: $localize`Contact`,
      message: $localize`Online contact is currently unavailable. Return to the store to continue browsing.`
    },
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./product/components/customer/order-history/order-history.component')
      .then(m => m.OrderHistoryComponent)
  },
  {
    path: 'faq',
    data: {title: $localize`Frequently asked questions`, message: $localize`Frequently asked questions are not published yet.`},
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
  {
    path: 'shipping-returns',
    data: {title: $localize`Shipping and returns`, message: $localize`Shipping and return information is not published on this page yet.`},
    loadComponent: () => import('./shared/components/info-page.component').then(m => m.InfoPageComponent)
  },
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
