import { Routes } from '@angular/router';
import { LoginComponent } from "./components/auth/login/login.component";
import { LogoutComponent } from "./components/auth/logout/logout.component";
import { SignupComponent } from "./components/auth/signup/signup.component";
import { CustomerDashboardComponent } from "./components/customer/customer-dashboard/customer-dashboard.component";
import { AdminDashboardComponent } from "./components/admin/admin-dashboard/admin-dashboard.component";
import { authGuard } from "./guards/auth.guard";
import { adminGuard } from "./guards/admin.guard";
import {
  ProductManagementComponent
} from "./components/admin/admin-product-management/admin-product-management.component";

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'register', component: SignupComponent },
  {
    path: 'dashboard',
    component: CustomerDashboardComponent,
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
      { path: '', redirectTo: 'categories', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];
