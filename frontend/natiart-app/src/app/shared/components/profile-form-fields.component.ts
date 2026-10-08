import {Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';
import {NatiartFormFieldComponent} from './natiart-form-field/natiart-form-field.component';
import {CpfFormatDirective} from '../../directory/directive/cpf-format-directive.directive';
import {PhoneFormatBrazilDirective} from '../../directory/directive/phone-format-brazil.directive';
import {AddressFormComponent} from './address-form/address-form.component';

@Component({
  selector: 'app-profile-form-fields',
  imports: [ReactiveFormsModule, NatiartFormFieldComponent, CpfFormatDirective, PhoneFormatBrazilDirective, AddressFormComponent],
  template: `
    <div [formGroup]="form" class="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <app-natiart-form-field label="First Name" controlName="firstname" [form]="form" i18n-label>
        <input class="form-input" formControlName="firstname" autocomplete="given-name" maxlength="100">
      </app-natiart-form-field>
      <app-natiart-form-field label="Last Name" controlName="lastname" [form]="form" i18n-label>
        <input class="form-input" formControlName="lastname" autocomplete="family-name" maxlength="100">
      </app-natiart-form-field>
      <app-natiart-form-field label="CPF" controlName="cpf" [form]="form" i18n-label>
        <input class="form-input" formControlName="cpf" cpfFormat inputmode="numeric" autocomplete="off" placeholder="000.000.000-00" maxlength="14">
      </app-natiart-form-field>
      <app-natiart-form-field label="Phone" controlName="phone" [form]="form" [isOptional]="true" i18n-label>
        <input class="form-input" formControlName="phone" appPhoneFormatBrazil type="tel" autocomplete="tel" placeholder="(XX) XXXXX-XXXX" i18n-placeholder maxlength="15">
      </app-natiart-form-field>
    </div>
    <div class="mt-8 border-t border-primary-light/40 pt-6">
      <app-address-form [addressFormGroup]="form" title="Address" i18n-title></app-address-form>
    </div>
  `
})
export class ProfileFormFieldsComponent {
  @Input({required: true}) form!: FormGroup;
}
