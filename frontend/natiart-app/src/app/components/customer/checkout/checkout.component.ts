import {Component, OnInit} from '@angular/core';
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {Observable} from "rxjs";
import {CartItem} from "../../../models/CartItem.model";
import {User} from "../../../models/user.model";
import {CartService} from "../../../service/cart.service";
import {OrderService} from "../../../service/order.service";
import {UserService} from "../../../service/user.service";
import {TokenService} from "../../../service/token.service";
import {RouterLink} from "@angular/router";

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    NgIf,
    AsyncPipe,
    RouterLink,
    ReactiveFormsModule,
    CurrencyPipe,
    NgForOf
  ],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css'
})
export class CheckoutComponent implements OnInit {
  checkoutForm: FormGroup;
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  isLoggedIn$: Observable<boolean>;
  currentUser$: Observable<User | null>;
  isLoading$: Observable<boolean>;

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private tokenService: TokenService,
    private userService: UserService,
    private orderService: OrderService
  ) {
    this.checkoutForm = this.fb.group({
      firstname: ['', Validators.required],
      lastname: ['', Validators.required],
      phone: ['', Validators.pattern('[0-9]*')],
      country: ['Brazil', Validators.required],
      state: ['', Validators.required],
      city: ['', Validators.required],
      neighborhood: ['', Validators.required],
      zipCode: ['', Validators.required],
      street: ['', Validators.required],
      complement: [''],
    });

    this.cartItems$ = this.cartService.getCartItems();
    this.cartTotal$ = this.cartService.getCartTotal();
    this.isLoggedIn$ = this.tokenService.isLoggedIn$;
    this.currentUser$ = this.userService.currentUser$;
    this.isLoading$ = this.orderService.orderProcessing$;
  }

  ngOnInit(): void {
    this.currentUser$.subscribe(user => {
      if (user) {
        this.checkoutForm.patchValue({
          name: user.profile.firstname,
          email: user.username
        });
      }
    });
  }

  useProfileAddress(): void {
    this.currentUser$.subscribe(user => {
      if (user && user.profile.country) {
        this.checkoutForm.patchValue({
          address: user.profile.street,
          city: user.profile.city,
          postalCode: user.profile.zipCode
        });
      }
    });
  }

  onSubmit(): void {
    if (this.checkoutForm.valid) {
      const orderData = {
        ...this.checkoutForm.value,
        //items: this.cartService.getCartItemsSnapshot()
      };
      this.orderService.createOrder(orderData).subscribe(
        () => {
          // Handle successful order placement (e.g., navigate to confirmation page)
          console.log('Order placed successfully');
        },
        error => {
          // Handle error (e.g., show error message)
          console.error('Error placing order:', error);
        }
      );
    }
  }
}
