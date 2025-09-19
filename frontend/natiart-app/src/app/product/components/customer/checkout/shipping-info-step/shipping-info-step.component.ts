import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";

import {Subject} from "rxjs";
import {takeUntil} from "rxjs/operators";
import {animate, style, transition, trigger} from "@angular/animations";
import {AddressFormComponent} from "../address-form/address-form.component";

@Component({
  selector: 'app-shipping-info-step',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    AddressFormComponent
],
  templateUrl: './shipping-info-step.component.html',
  animations: [
    trigger('slideInRight', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('300ms ease-out', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({ transform: 'translateX(100%)', opacity: 0 }))
      ])
    ]),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShippingInfoStepComponent implements OnInit, OnDestroy {
  @Input({ required: true }) checkoutForm!: FormGroup;
  // Manage 'sameShippingAsBilling' locally
  @Input() sameShippingAsBillingInitialValue: boolean = true;
  @Output() sameShippingAsBillingChange = new EventEmitter<boolean>();

  _sameShippingAsBilling: boolean = true;

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this._sameShippingAsBilling = this.sameShippingAsBillingInitialValue;
    this.setupBillingInfoSync();
    this.updateBillingValidators();
  }

  get shippingInfoFormGroup(): FormGroup {
    return this.checkoutForm.get('shippingInfo') as FormGroup;
  }

  get billingInfoFormGroup(): FormGroup {
    return this.checkoutForm.get('billingInfo') as FormGroup;
  }

  toggleSameShippingAsBilling(event: Event): void {
    this._sameShippingAsBilling = (event.target as HTMLInputElement).checked;
    this.sameShippingAsBillingChange.emit(this._sameShippingAsBilling);
    this.updateBillingValidators();
    if (this._sameShippingAsBilling) {
      this.syncShippingToBilling();
    }
  }

  private setupBillingInfoSync(): void {
    this.shippingInfoFormGroup.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this._sameShippingAsBilling) {
          this.syncShippingToBilling();
        }
      });
  }

  private syncShippingToBilling(): void {
    this.billingInfoFormGroup.patchValue(this.shippingInfoFormGroup.value);
  }

  private updateBillingValidators(): void {
    const fields = ['country', 'state', 'city', 'neighborhood', 'zipCode', 'street'];
    fields.forEach(field => {
      const control = this.billingInfoFormGroup.get(field);
      if (this._sameShippingAsBilling) {
        control?.clearValidators();
      } else {
        control?.setValidators([Validators.required]);
      }
      control?.updateValueAndValidity({ emitEvent: false });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
