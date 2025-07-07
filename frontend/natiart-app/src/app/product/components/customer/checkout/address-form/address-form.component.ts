import {ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {finalize, Subject, takeUntil} from 'rxjs';
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
    CommonModule,
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

  constructor(private signupService: SignupService) {}

  ngOnInit(): void {
    if (this.zipCodeLookupEnabled) {
      this.addressFormGroup.get('zipCode')?.valueChanges
        .pipe(takeUntil(this.destroy$))
        .subscribe(zip => this.onZipCodeChange(zip));
    }
  }

  private onZipCodeChange(zipCode: string | null): void {
    this.clearErrorMessage();
    const cleanZipCode = zipCode?.replace(/\D/g, '');
    const zipCodeControl = this.addressFormGroup.get('zipCode');

    if (!cleanZipCode || cleanZipCode.length !== 8) {
      // If CEP is not 8 digits or empty, clear address fields and mark zipCode as invalid
      this.addressFormGroup.patchValue({
        street: '', city: '', neighborhood: '', state: '', country: ''
      });
      // Only set error if it's not empty, otherwise rely on Validators.required
      if (cleanZipCode && cleanZipCode.length !== 8) {
        zipCodeControl?.setErrors({ 'invalidCepLength': true });
      } else {
        zipCodeControl?.setErrors(null); // Clear any previous errors if empty
      }
      this.addressFormGroup.updateValueAndValidity(); // Update parent form group validity
      return;
    }

    this.isLoadingAddress = true;
    this.signupService.getAddressFromZipCode(cleanZipCode)
      .pipe(
        finalize(() => {
          this.isLoadingAddress = false;
          this.addressFormGroup.updateValueAndValidity(); // Update parent form group validity after loading
        }),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (data: ViaCEPResponse) => {
          if (data.erro) {
            this.setErrorMessage('CEP not found. Please enter address manually.');
            this.addressFormGroup.patchValue({
              street: '', city: '', neighborhood: '', state: '', country: ''
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
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
          this.addressFormGroup.patchValue({
            street: '', city: '', neighborhood: '', state: '', country: ''
          });
          zipCodeControl?.setErrors({ 'fetchError': true });
          // Mark all relevant controls as touched and dirty to show validation messages
          ['street', 'city', 'neighborhood', 'state', 'country'].forEach(controlName => {
            const control = this.addressFormGroup.get(controlName);
            control?.markAsTouched();
            control?.markAsDirty();
            control?.updateValueAndValidity();
          });
        }
      });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
