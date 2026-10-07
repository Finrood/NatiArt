import {Component} from '@angular/core';
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {ProductListComponent} from "./product-list/product-list.component";
import {TopBannerComponent} from "./top-banner/top-banner.component";
import {TopMenuComponent} from "../top-menu/top-menu.component";

@Component({
  selector: 'app-dashboard',
  imports: [
    LeftMenuComponent,
    ProductListComponent,
    TopBannerComponent,
    TopMenuComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {

}
