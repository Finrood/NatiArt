import {ChangeDetectionStrategy, ChangeDetectorRef, Component, input, Input, OnDestroy, OnInit, inject} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';

import {debounceTime, finalize, Subject, Subscription, takeUntil, tap} from 'rxjs';
import {SignupService} from "../../../directory/service/signup.service";
import {ViaCEPResponse} from "../../../directory/models/viaCEPResponse.model";
import {
  NatiartFormFieldComponent
} from "../natiart-form-field/natiart-form-field.component";
import {
  LoadingSpinnerComponent
} from "../shared/loading-spinner/loading-spinner.component";
import {CepFormatDirective} from "../../../directory/directive/cep-format-directive.directive";

@Component({
  selector: 'app-address-form',
  imports: [
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    LoadingSpinnerComponent,
    CepFormatDirective
],
  templateUrl: './address-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AddressFormComponent implements OnInit, OnDestroy {
  @Input({ required: true }) addressFormGroup!: FormGroup;
  @Input() title: string = $localize`Address`;
  readonly $showTitle = input<boolean>(true, {alias: 'showTitle'});

  get zipCodeLabel(): string {
    return this.isBillingAddress ? $localize`Billing Zip Code` : $localize`Zip Code`;
  }
  @Input() zipCodeLookupEnabled: boolean = true;
  @Input() isBillingAddress: boolean = false; // To slightly change IDs for uniqueness

  isLoadingAddress = false;
  errorMessage = '';

  private destroy$ = new Subject<void>();
  private lookupSubscription: Subscription | null = null;
  private addressAtZipChange: Record<string, string> = {};
  private readonly addressFields: string[] = ['street', 'city', 'neighborhood', 'state'];
  private readonly CEP_DEBOUNCE_MS: number = 400;

  private readonly _signupService: SignupService = inject(SignupService);
  private readonly cdr: ChangeDetectorRef = inject(ChangeDetectorRef);

  ngOnInit(): void {
    if (this.zipCodeLookupEnabled) {
      this.addressFormGroup.get('zipCode')?.valueChanges
        .pipe(
          tap((): void => {
            this.stopLookup();
            this.addressAtZipChange = Object.fromEntries(this.addressFields.map((field: string): [string, string] =>
              [field, this.addressFormGroup.get(field)!.value]));
          }),
          debounceTime(this.CEP_DEBOUNCE_MS),
          takeUntil(this.destroy$)
        )
        .subscribe(zip => this.onZipCodeChange(zip));
    }
  }

  private onZipCodeChange(zipCode: string | null): void {
    this.clearErrorMessage();
    const cleanZipCode = zipCode?.replace(/\D/g, '');
    const zipCodeControl = this.addressFormGroup.get('zipCode');

    if (!cleanZipCode || cleanZipCode.length !== 8) {
      // A failed lookup is still a valid manual-entry flow. Keep the country
      // value because it is intentionally read-only in this form.
      // CEP syntax belongs to the control validators, independently of lookup availability.
      zipCodeControl?.updateValueAndValidity({emitEvent: false});
      this.addressFormGroup.updateValueAndValidity(); // Update parent form group validity
      this.cdr.markForCheck();
      return;
    }

    this.isLoadingAddress = true;
    this.cdr.markForCheck();
    this.lookupSubscription = this._signupService.getAddressFromZipCode(cleanZipCode)
      .pipe(
        finalize(() => {
          this.isLoadingAddress = false;
          // Stopping a lookup when moving to Payment must not invalidate an already reviewed quote.
          this.addressFormGroup.updateValueAndValidity({emitEvent: false});
          this.cdr.markForCheck();
        }),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (data: ViaCEPResponse) => {
          if (data.erro) {
            this.setErrorMessage($localize`CEP not found. Please enter address manually.`);
            zipCodeControl?.updateValueAndValidity({emitEvent: false});
          } else {
            const suggested: Record<string, string> = {
              street: data.logradouro, city: data.localidade, neighborhood: data.bairro, state: data.uf
            };
            for (const field of this.addressFields) {
              const control = this.addressFormGroup.get(field)!;
              // Preserve corrections typed while the lookup was waiting or in flight.
              if (control.value === this.addressAtZipChange[field]) control.setValue(suggested[field] || control.value);
            }
            zipCodeControl?.updateValueAndValidity({emitEvent: false});
          }
          // Mark all relevant controls as touched and dirty to show validation messages
          ['street', 'city', 'neighborhood', 'state', 'country'].forEach(controlName => {
            const control = this.addressFormGroup.get(controlName);
            control?.markAsTouched();
            control?.markAsDirty();
            control?.updateValueAndValidity();
          });
          this.cdr.markForCheck();
        },
        error: () => {
          this.setErrorMessage($localize`Error fetching address. Please enter manually.`);
          zipCodeControl?.updateValueAndValidity({emitEvent: false});
          // Mark all relevant controls as touched and dirty to show validation messages
          ['street', 'city', 'neighborhood', 'state', 'country'].forEach(controlName => {
            const control = this.addressFormGroup.get(controlName);
            control?.markAsTouched();
            control?.markAsDirty();
            control?.updateValueAndValidity();
          });
          this.cdr.markForCheck();
        }
      });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
    this.cdr.markForCheck();
  }

  private stopLookup(): void {
    if (this.lookupSubscription) {
      this.lookupSubscription.unsubscribe();
      this.lookupSubscription = null;
    }
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
    this.cdr.markForCheck();
  }

  ngOnDestroy(): void {
    this.stopLookup();
    this.destroy$.next();
    this.destroy$.complete();
  }
}
