import {Component, EventEmitter, Input, Output} from '@angular/core';
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {CartItem} from "../../../../models/CartItem.model";

@Component({
  selector: 'app-order-summary',
  standalone: true,
    imports: [
        AsyncPipe,
        CurrencyPipe,
        NgForOf,
        NgIf
    ],
  templateUrl: './order-summary.component.html',
  styleUrl: './order-summary.component.css'
})
export class OrderSummaryComponent {
  @Input() cartItems: CartItem[] | null = null;
  @Input() cartTotal: number | null = 0;
}
