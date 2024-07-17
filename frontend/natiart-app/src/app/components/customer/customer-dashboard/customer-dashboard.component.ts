import {Component} from '@angular/core';
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [
    TopMenuComponent,
    LeftMenuComponent
  ],
  templateUrl: './customer-dashboard.component.html',
  styleUrl: './customer-dashboard.component.css'
})
export class CustomerDashboardComponent {

}
