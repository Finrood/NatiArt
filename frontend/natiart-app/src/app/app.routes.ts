import {Routes} from '@angular/router';
import {LoginComponent} from "./components/auth/login/login.component";
import {LogoutComponent} from "./components/auth/logout/logout.component";
import {SignupComponent} from "./components/auth/signup/signup.component";
import {CustomerDashboardComponent} from "./components/customer/customer-dashboard/customer-dashboard.component";
import {AdminDashboardComponent} from "./components/admin/admin-dashboard/admin-dashboard.component";
import {authGuard} from "./guards/auth.guard";
import {adminGuard} from "./guards/admin.guard";
import {ProductDetailComponent} from "./components/customer/product-detail/product-detail.component";
import {productGuard} from "./guards/product-guard.guard";
import {CartComponent} from "./components/customer/cart/cart.component";
import {CheckoutComponent} from "./components/customer/checkout/checkout.component";
import {
  PixPaymentConfirmationComponent
} from "./components/customer/checkout/pix-payment-confirmation/pix-payment-confirmation.component";

export const routes: Routes = [
  {path: 'login', component: LoginComponent},
  {path: 'logout', component: LogoutComponent},
  {path: 'register', component: SignupComponent},
  {
    path: 'dashboard',
    component: CustomerDashboardComponent,
    canActivate: [authGuard]
  },
  {
    path: 'product/:id',
    component: ProductDetailComponent,
    canActivate: [authGuard],
    canDeactivate: [productGuard]
  },
  {
    path: 'cart',
    component: CartComponent,
    canActivate: [authGuard]
  },
  {
    path: 'checkout',
    component: CheckoutComponent,
    canActivate: [authGuard]
  },
  {
    path: 'pix-payment/:paymentId',
    component: PixPaymentConfirmationComponent,
    canActivate: [authGuard]
  },
  {
    path: 'admin',
    component: AdminDashboardComponent,
    canActivate: [authGuard, adminGuard],
    children: [
      {
        path: 'dashboard',
        redirectTo: 'categories',
        pathMatch: 'full'
      },
      {
        path: 'categories',
        loadComponent: () => import('./components/admin/admin-category-management/admin-category-management.component')
          .then(m => m.CategoryManagementComponent)
      },
      {
        path: 'products',
        loadComponent: () => import('./components/admin/admin-product-management/admin-product-management.component')
          .then(m => m.ProductManagementComponent)
      },
      {
        path: 'packages',
        loadComponent: () => import('./components/admin/admin-package-management/admin-package-management.component')
          .then(m => m.PackageManagementComponent)
      },
      {path: '', redirectTo: 'categories', pathMatch: 'full'}
    ]
  },
  {path: '', redirectTo: '/dashboard', pathMatch: 'full'},
  {path: '**', redirectTo: '/dashboard'}
];
