import {ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';

import {debounceTime, distinctUntilChanged, finalize, Subject, Subscription, takeUntil} from 'rxjs';
import {SignupService} from "../../../../../directory/service/signup.service";
import {ViaCEPResponse} from "../../../../../directory/models/viaCEPResponse.model";
import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {
  LoadingSpinnerComponent
} from "../../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {CepFormatDirective} from "../../../../../directory/directive/cep-format-directive.directive";

@Component({
  selector: 'app-address-form',
  standalone: true,
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
  @Input() title: string = 'Address';
  @Input() zipCodeLookupEnabled: boolean = true;
  @Input() isBillingAddress: boolean = false; // To slightly change IDs for uniqueness

  isLoadingAddress = false;
  errorMessage = '';

  private destroy$ = new Subject<void>();
  private lookupSubscription: Subscription | null = null;
  private readonly CEP_DEBOUNCE_MS: number = 400;

  constructor(private signupService: SignupService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    if (this.zipCodeLookupEnabled) {
      this.addressFormGroup.get('zipCode')?.valueChanges
        .pipe(
          debounceTime(this.CEP_DEBOUNCE_MS),
          distinctUntilChanged(),
          takeUntil(this.destroy$)
        )
        .subscribe(zip => this.onZipCodeChange(zip));
    }
  }

  private onZipCodeChange(zipCode: string | null): void {
    this.stopLookup();
    this.clearErrorMessage();
    const cleanZipCode = zipCode?.replace(/\D/g, '');
    const zipCodeControl = this.addressFormGroup.get('zipCode');

    if (!cleanZipCode || cleanZipCode.length !== 8) {
      // A failed lookup is still a valid manual-entry flow. Keep the country
      // value because it is intentionally read-only in this form.
      this.addressFormGroup.patchValue({
        street: '', city: '', neighborhood: '', state: '', country: 'Brazil'
      });
      // Only set error if it's not empty, otherwise rely on Validators.required
      if (cleanZipCode && cleanZipCode.length !== 8) {
        zipCodeControl?.setErrors({ 'invalidCepLength': true });
      } else {
        zipCodeControl?.setErrors(null); // Clear any previous errors if empty
      }
      this.addressFormGroup.updateValueAndValidity(); // Update parent form group validity
      this.cdr.markForCheck();
      return;
    }

    this.isLoadingAddress = true;
    this.cdr.markForCheck();
    this.lookupSubscription = this.signupService.getAddressFromZipCode(cleanZipCode)
      .pipe(
        finalize(() => {
          this.isLoadingAddress = false;
          this.addressFormGroup.updateValueAndValidity(); // Update parent form group validity after loading
          this.cdr.markForCheck();
        }),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (data: ViaCEPResponse) => {
          if (data.erro) {
            this.setErrorMessage('CEP not found. Please enter address manually.');
            this.addressFormGroup.patchValue({
              street: '', city: '', neighborhood: '', state: '', country: 'Brazil'
            });
            zipCodeControl?.setErrors({ 'cepNotFound': true });
          } else {
            this.addressFormGroup.patchValue({
              street: data.logradouro,
              city: data.localidade,
              neighborhood: data.bairro,
              state: data.uf,
              country: "Brazil", // Assuming Brazil is default
            });
            zipCodeControl?.setErrors(null); // Clear CEP specific errors on success
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
          this.setErrorMessage('Error fetching address. Please enter manually.');
          this.addressFormGroup.patchValue({
            street: '', city: '', neighborhood: '', state: '', country: 'Brazil'
          });
          zipCodeControl?.setErrors({ 'fetchError': true });
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
