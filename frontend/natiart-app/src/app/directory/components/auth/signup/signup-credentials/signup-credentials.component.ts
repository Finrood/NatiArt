import {Component, EventEmitter, Input, Output} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {NatiartFormFieldComponent} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {ButtonComponent} from "../../../../../shared/components/button.component";
import {PasswordRequirementsComponent} from "../password-requirements/password-requirements.component";

@Component({
  selector: 'app-signup-credentials',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    PasswordRequirementsComponent,
    ButtonComponent
  ],
  templateUrl: './signup-credentials.component.html',
  styleUrl: './signup-credentials.component.css'
})
export class SignupCredentialsComponent {
  @Input() credentialForm!: FormGroup;
  @Output() nextStep = new EventEmitter<void>();

  goToNext() {
    this.nextStep.emit();
  }

  onEnter() {
    if (this.credentialForm.valid) {
      this.goToNext();
    }
  }
}
