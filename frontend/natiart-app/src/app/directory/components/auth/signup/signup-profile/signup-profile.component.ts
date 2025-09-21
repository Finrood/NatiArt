import {Component, EventEmitter, Input, Output} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {PhoneFormatBrazilDirective} from "../../../../directive/phone-format-brazil.directive";
import {CpfFormatDirective} from "../../../../directive/cpf-format-directive.directive";
import {CepFormatDirective} from "../../../../directive/cep-format-directive.directive";
import {
  LoadingSpinnerComponent
} from "../../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {ButtonComponent} from "../../../../../shared/components/button.component";

import {finalize} from "rxjs/operators";
import {ViaCEPResponse} from "../../../../models/viaCEPResponse.model";
import {SignupService} from "../../../../service/signup.service";

@Component({
  selector: 'app-signup-profile',
  standalone: true,
  imports: [
    NatiartFormFieldComponent,
    ReactiveFormsModule,
    PhoneFormatBrazilDirective,
    CpfFormatDirective,
    CepFormatDirective,
    LoadingSpinnerComponent,
    ButtonComponent
],
  templateUrl: './signup-profile.component.html',
  styleUrl: './signup-profile.component.css'
})
export class SignupProfileComponent {
  @Input() profileForm!: FormGroup;
  @Input() errorMessage = '';
  @Output() previousStep = new EventEmitter<void>();
  @Output() nextStep = new EventEmitter<void>();

  isLoadingAddress = false;

  constructor(private signupService: SignupService) {
  }

  goBack() {
    this.previousStep.emit();
  }

  goToNext() {
    this.nextStep.emit();
  }

  onEnter() {
    if (this.profileForm.valid) {
      this.goToNext();
    }
  }

  onZipCodeChange(): void {
    this.clearErrorMessage();
    const zipCode = this.profileForm.get('zipCode')?.value?.replace(/\D/g, '');
    if (zipCode?.length !== 8) {
      return;
    }

    this.isLoadingAddress = true;
    this.signupService.getAddressFromZipCode(zipCode)
      .pipe(finalize(() => this.isLoadingAddress = false))
      .subscribe({
        next: (data: ViaCEPResponse) => {
          this.profileForm.patchValue({
              street: data.logradouro,
              city: data.localidade,
              neighborhood: data.bairro,
              state: data.uf,
              country: "Brazil",
          });
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }
}
