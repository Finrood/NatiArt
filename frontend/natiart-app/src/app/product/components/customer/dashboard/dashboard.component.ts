import {Component, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {ProductListComponent} from "./product-list/product-list.component";
import {TopBannerComponent} from "./top-banner/top-banner.component";

@Component({
  selector: 'app-dashboard',
  imports: [
    ProductListComponent,
    TopBannerComponent,
    RouterLink
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  readonly $featuredIds = signal<string[]>([]);
}
