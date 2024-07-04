import { Routes } from '@angular/router';
import {LoginComponent} from "./components/auth/login/login.component";
import {LogoutComponent} from "./components/auth/logout/logout.component";
import {SignupComponent} from "./components/auth/signup/signup.component";
import {CustomerDashboardComponent} from "./components/customer/customer-dashboard/customer-dashboard.component";
import {authGuard} from "./guards/auth.guard";
import {adminGuard} from "./guards/admin.guard";

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'logout', component: LogoutComponent },
  { path: 'register', component: SignupComponent },
  { path: 'dashboard', component: CustomerDashboardComponent, canActivate: [authGuard, adminGuard] },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];
