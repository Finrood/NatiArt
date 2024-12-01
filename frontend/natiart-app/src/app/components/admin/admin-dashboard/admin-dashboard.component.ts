import {Component} from '@angular/core';
import {NgForOf} from "@angular/common";
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
    imports: [NgForOf, RouterLink, RouterLinkActive, RouterOutlet]
})
export class AdminDashboardComponent {
  menuItems = [
    {path: 'categories', label: 'Categories'},
    {path: 'products', label: 'Products'},
    {path: 'packages', label: 'Packages'}
  ];

  getButtonClasses(isActive: boolean): string {
    const baseClasses = 'px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-opacity-50';
    const activeClasses = 'bg-primary text-white hover:bg-primary-dark focus:ring-primary';
    const inactiveClasses = 'bg-gray-200 text-gray-700 hover:bg-gray-300 focus:ring-gray-400';

    return `${baseClasses} ${isActive ? activeClasses : inactiveClasses}`;
  }
}
