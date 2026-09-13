import {Component} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {finalize} from 'rxjs/operators';

import {ButtonComponent} from '../../../../shared/components/button.component';
import {NatiartFormFieldComponent} from '../../../../shared/components/natiart-form-field/natiart-form-field.component';
import {PasswordResetService} from '../../../service/password-reset.service';
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-password-reset-request',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, NatiartFormFieldComponent],
  templateUrl: './password-reset-request.component.html'
})
export class PasswordResetRequestComponent {
  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.email]]
  });
  isSubmitting = false;
  message = '';
  errorMessage = '';

  constructor(private readonly fb: FormBuilder, private readonly passwordResetService: PasswordResetService) {}

  submit(): void {
    if (this.isSubmitting) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.message = '';
    this.errorMessage = '';
    this.passwordResetService.requestReset(this.form.controls.username.value)
      .pipe(finalize(() => this.isSubmitting = false))
      .subscribe({
        next: response => this.message = response.message,
        error: (error: HttpErrorResponse) => {
          this.errorMessage = 'We could not process that request. Please try again shortly.';
          reportError('password-reset-request', error);
        }
      });
  }
}
