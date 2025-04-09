import {Routes} from '@angular/router';
import {LoginComponent} from "./directory/components/auth/login/login.component";
import {LogoutComponent} from "./directory/components/auth/logout/logout.component";
import {SignupComponent} from "./directory/components/auth/signup/signup.component";
import {AdminDashboardComponent} from "./product/components/admin/admin-dashboard/admin-dashboard.component";
import {DashboardComponent} from "./product/components/customer/dashboard/dashboard.component";

export const routes: Routes = [
  {path: 'login', component: LoginComponent},
  {path: 'logout', component: LogoutComponent},
  {path: 'register', component: SignupComponent},
  {
    path: 'dashboard',
    component: DashboardComponent,
    // canActivate: [authGuard]
  },
  // {
  //   path: 'product/:id',
  //   component: ProductDetailComponent,
  //   canActivate: [authGuard],
  //   canDeactivate: [productGuard]
  // },
  // {
  //   path: 'cart',
  //   component: CartComponent,
  //   canActivate: [authGuard]
  // },
  // {
  //   path: 'checkout',
  //   component: CheckoutComponent,
  //   canActivate: [authGuard]
  // },
  // {
  //   path: 'pix-payment/:paymentId',
  //   component: PixPaymentConfirmationComponent,
  //   canActivate: [authGuard]
  // },
  {
    path: 'admin',
    component: AdminDashboardComponent,
    // canActivate: [authGuard, adminGuard],
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
