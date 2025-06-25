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
    if (cleanZipCode?.length !== 8) {
      return;
    }

    this.isLoadingAddress = true;
    this.signupService.getAddressFromZipCode(cleanZipCode)
      .pipe(
        finalize(() => this.isLoadingAddress = false),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (data: ViaCEPResponse) => {
          if (data.erro) {
            this.setErrorMessage('CEP not found. Please enter address manually.');
            this.addressFormGroup.patchValue({
              street: '', city: '', neighborhood: '', state: '', country: 'Brazil'
            });
          } else {
            this.addressFormGroup.patchValue({
              street: data.logradouro,
              city: data.localidade,
              neighborhood: data.bairro,
              state: data.uf,
              country: "Brazil", // Assuming Brazil is default
            });
          }
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
    // Consider emitting an event if the parent needs to know about this error.
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
