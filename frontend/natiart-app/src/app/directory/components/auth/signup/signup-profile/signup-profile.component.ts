import {Component, EventEmitter, Input, Output} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';
import {ProfileFormFieldsComponent} from '../../../../../shared/components/profile-form-fields.component';
import {ButtonComponent} from '../../../../../shared/components/button.component';

@Component({selector: 'app-signup-profile', imports: [ReactiveFormsModule, ProfileFormFieldsComponent, ButtonComponent],
  templateUrl: './signup-profile.component.html', styleUrl: './signup-profile.component.css'})
export class SignupProfileComponent {
  @Input() profileForm!: FormGroup;
  @Input() errorMessage: string = '';
  @Input() isSubmitting: boolean = false;
  @Output() previousStep = new EventEmitter<void>();
  @Output() nextStep = new EventEmitter<void>();
  goBack(): void { this.previousStep.emit(); }
  goToNext(): void { this.nextStep.emit(); }
  onEnter(): void { if (this.profileForm.valid) this.goToNext(); }
}
