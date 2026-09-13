import {Component, OnInit} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {finalize} from 'rxjs/operators';

import {ButtonComponent} from '../../../../shared/components/button.component';
import {NatiartFormFieldComponent} from '../../../../shared/components/natiart-form-field/natiart-form-field.component';
import {PasswordRequirementsComponent} from '../signup/password-requirements/password-requirements.component';
import {CustomPasswordValidators} from '../../../validator/CustomPasswordValidators';
import {PasswordResetService} from '../../../service/password-reset.service';
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-password-reset',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, NatiartFormFieldComponent, PasswordRequirementsComponent],
  templateUrl: './password-reset.component.html'
})
export class PasswordResetComponent implements OnInit {
  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]],
    passwordConfirmation: ['', Validators.required]
  }, {validators: PasswordResetComponent.passwordMatchValidator});
  token = '';
  isSubmitting = false;
  success = false;
  errorMessage = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly passwordResetService: PasswordResetService
  ) {}

  ngOnInit(): void {
    const fragment = this.route.snapshot.fragment ?? '';
    this.token = fragment.startsWith('token=') ? decodeURIComponent(fragment.substring(6)) : '';
  }

  submit(): void {
    if (this.isSubmitting) {
      return;
    }
    if (!this.token) {
      this.errorMessage = 'This reset link is missing or invalid. Please request a new one.';
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    const {password, passwordConfirmation} = this.form.getRawValue();
    this.passwordResetService.resetPassword(this.token, password, passwordConfirmation)
      .pipe(finalize(() => this.isSubmitting = false))
      .subscribe({
        next: () => {
          this.success = true;
          void this.router.navigate(['/login']);
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage = 'This reset link is invalid or expired. Please request a new one.';
          reportError('password-reset', error);
        }
      });
  }

  private static readonly passwordMatchValidator = (group: import('@angular/forms').AbstractControl) => {
    const password = group.get('password')?.value;
    const confirmation = group.get('passwordConfirmation')?.value;
    return password === confirmation ? null : {passwordMismatch: true};
  };
}
