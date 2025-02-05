import {Component, Input} from '@angular/core';
import {CepFormatDirective} from "../../../../../directory/directive/cep-format-directive.directive";
import {FormGroup, FormsModule, ReactiveFormsModule} from "@angular/forms";
import {LoadingSpinnerComponent} from "../../../shared/loading-spinner/loading-spinner.component";
import {NgClass, NgIf} from "@angular/common";
import {finalize} from "rxjs/operators";
import {SignupService, ViaCEPResponse} from "../../../../service/signup.service";

@Component({
    selector: 'app-shipping-info-step',
    imports: [
        CepFormatDirective,
        FormsModule,
        LoadingSpinnerComponent,
        NgIf,
        ReactiveFormsModule,
        NgClass
    ],
    templateUrl: './shipping-info-step.component.html',
    styleUrl: './shipping-info-step.component.css'
})
export class ShippingInfoStepComponent {
  @Input() checkoutForm!: FormGroup;
  @Input() sameShippingAsBilling!: boolean;

  isLoadingShippingAddress = false;
  isLoadingBillingAddress = false;
  errorMessage = '';

  constructor(private signupService: SignupService) {
  }

  getFieldError(fieldName: string): string {
    const field = this.checkoutForm.get(fieldName);
    if (field?.invalid && (field.dirty || field.touched)) {
      if (field.errors?.['required']) return 'This field is required.';
    }
    return '';
  }

  onZipCodeChange(section: 'shippingInfo' | 'billingInfo'): void {
    const zipCode = this.checkoutForm.get(`${section}.zipCode`)?.value?.replace(/\D/g, '');
    if (zipCode?.length !== 8) {
      return;
    }

    if (section === 'shippingInfo') {
      this.isLoadingShippingAddress = true;
    } else {
      this.isLoadingBillingAddress = true;
    }

    this.signupService.getAddressFromZipCode(zipCode)
      .pipe(finalize(() => {
        this.isLoadingShippingAddress = false;
        this.isLoadingBillingAddress = false;
      }))
      .subscribe({
        next: (data: ViaCEPResponse) => {
          const addressData = {
            street: data.logradouro,
            city: data.localidade,
            neighborhood: data.bairro,
            state: data.uf,
            country: "Brazil",
          };

          const updatedFormValue = {
            [section]: addressData
          };

          this.checkoutForm.patchValue(updatedFormValue);
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }

  toggleSameShippingAsBilling(event: Event): void {
    this.sameShippingAsBilling = (event.target as HTMLInputElement).checked;
    if (this.sameShippingAsBilling) {
      this.checkoutForm.get('billingInfo')?.patchValue({
        country: this.checkoutForm.get('shippingInfo.country')?.value,
        state: this.checkoutForm.get('shippingInfo.state')?.value,
        city: this.checkoutForm.get('shippingInfo.city')?.value,
        neighborhood: this.checkoutForm.get('shippingInfo.neighborhood')?.value,
        zipCode: this.checkoutForm.get('shippingInfo.zipCode')?.value,
        street: this.checkoutForm.get('shippingInfo.street')?.value,
        complement: this.checkoutForm.get('shippingInfo.complement')?.value,
      });
    }
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }
}
