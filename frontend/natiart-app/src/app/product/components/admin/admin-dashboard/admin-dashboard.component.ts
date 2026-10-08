import {Component} from '@angular/core';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="art-page">
      <h1 data-page-heading class="art-title mb-8"><ng-container i18n>Admin Dashboard</ng-container></h1>
      <nav class="admin-navigation mb-8 flex flex-wrap gap-2">
        @for (item of menuItems; track item) {
          <a
            [routerLink]="['/admin', item.path]"
            routerLinkActive="current" ariaCurrentWhenActive="page">
            {{ item.label }}
          </a>
        }
      </nav>
      <router-outlet></router-outlet>
    </div>
    `,
  styles: [`
    .admin-navigation { border-bottom: 1px solid rgb(var(--primary-light) / .5); }
    .admin-navigation a { display: inline-flex; align-items: center; min-height: 3rem; padding: .75rem 1rem; border-bottom: 2px solid transparent; font-size: .875rem; color: rgb(var(--text-muted)); }
    .admin-navigation a.current { color: rgb(var(--primary)); border-bottom-color: rgb(var(--primary)); font-weight: 500; }
    .admin-navigation a:hover { background: rgb(var(--background-variant)); }
  `],
})
export class AdminDashboardComponent {
  menuItems = [
    { path: 'categories', label: $localize`Categories` },
    { path: 'products', label: $localize`Products` },
    { path: 'packages', label: $localize`Packages` },
    { path: 'orders', label: $localize`Orders` },
  ];
}
