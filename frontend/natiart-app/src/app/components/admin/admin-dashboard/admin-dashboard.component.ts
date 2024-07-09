import { Component } from '@angular/core';
import {CategoryManagementComponent} from "../admin-category-management/admin-category-management.component";
import {ProductManagementComponent} from "../admin-product-management/admin-product-management.component";
import {NgForOf, NgSwitch, NgSwitchCase} from "@angular/common";
import {RouterLink, RouterLinkActive, RouterOutlet} from "@angular/router";

@Component({
  selector: 'app-admin-dashboard',
  template: `
    <div class="container mx-auto px-4 py-8">
      <h1 class="text-3xl font-light mb-8 text-gray-800">Admin Dashboard</h1>

      <div class="mb-8">
        <nav class="flex space-x-4">
          <a
            *ngFor="let item of menuItems"
            [routerLink]="['/admin', item.path]"
            routerLinkActive #rla="routerLinkActive"
            [class]="getButtonClasses(rla.isActive)"
          >
            {{ item.label }}
          </a>
        </nav>
      </div>

      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  imports: [CategoryManagementComponent, ProductManagementComponent, NgSwitch, NgForOf, NgSwitchCase, RouterLink, RouterLinkActive, RouterOutlet]
})
export class AdminDashboardComponent {
  menuItems = [
    { path: 'categories', label: 'Categories' },
    { path: 'products', label: 'Products' }
  ];

  getButtonClasses(isActive: boolean): string {
    const baseClasses = 'px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-opacity-50';
    const activeClasses = 'bg-[#D4A59A] text-white hover:bg-[#C89F94] focus:ring-[#D4A59A]';
    const inactiveClasses = 'bg-gray-200 text-gray-700 hover:bg-gray-300 focus:ring-gray-400';

    return `${baseClasses} ${isActive ? activeClasses : inactiveClasses}`;
  }
}
