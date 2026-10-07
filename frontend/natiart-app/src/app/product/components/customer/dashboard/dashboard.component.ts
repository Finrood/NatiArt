import {Component} from '@angular/core';
import {RouterLink} from '@angular/router';
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {ProductListComponent} from "./product-list/product-list.component";
import {TopBannerComponent} from "./top-banner/top-banner.component";
import {ButtonComponent} from "../../../../shared/components/button.component";

@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink,
    LeftMenuComponent,
    ProductListComponent,
    TopBannerComponent,
    ButtonComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {

}
