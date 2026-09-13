import {ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output} from '@angular/core';
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
import {Subscription} from "rxjs";
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
export class SignupProfileComponent implements OnDestroy {
  @Input() profileForm!: FormGroup;
  @Input() errorMessage = '';
  @Input() isSubmitting = false;
  @Output() previousStep = new EventEmitter<void>();
  @Output() nextStep = new EventEmitter<void>();

  isLoadingAddress = false;
  addressErrorMessage = '';
  private addressLookupSubscription: Subscription | undefined;

  constructor(private signupService: SignupService, private changeDetectorRef: ChangeDetectorRef) {
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
    this.addressErrorMessage = '';
    this.addressLookupSubscription?.unsubscribe();
    const zipCode = this.profileForm.get('zipCode')?.value?.replace(/\D/g, '');
    if (zipCode?.length !== 8) {
      return;
    }

    this.isLoadingAddress = true;
    this.addressLookupSubscription = this.signupService.getAddressFromZipCode(zipCode)
      .pipe(finalize(() => {
        this.isLoadingAddress = false;
        this.changeDetectorRef.markForCheck();
      }))
      .subscribe({
        next: (data: ViaCEPResponse) => {
          if (data.erro) {
            this.setAddressErrorMessage('This ZIP code was not found. Please enter the address manually.');
            return;
          }
          this.profileForm.patchValue({
              street: data.logradouro,
              city: data.localidade,
              neighborhood: data.bairro,
              state: data.uf,
              country: "Brazil",
          });
        },
        error: () => {
          this.setAddressErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }

  ngOnDestroy(): void {
    this.addressLookupSubscription?.unsubscribe();
  }

  private setAddressErrorMessage(message: string): void {
    this.addressErrorMessage = message;
    this.changeDetectorRef.markForCheck();
  }
}
