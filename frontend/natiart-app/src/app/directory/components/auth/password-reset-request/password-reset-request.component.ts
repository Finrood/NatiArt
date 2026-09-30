import {Component, DestroyRef, inject, signal, WritableSignal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {finalize} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

import {ButtonComponent} from '../../../../shared/components/button.component';
import {NatiartFormFieldComponent} from '../../../../shared/components/natiart-form-field/natiart-form-field.component';
import {PasswordResetService} from '../../../service/password-reset.service';
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-password-reset-request',
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, NatiartFormFieldComponent],
  templateUrl: './password-reset-request.component.html'
})
export class PasswordResetRequestComponent {
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _passwordResetService: PasswordResetService = inject(PasswordResetService);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);
  readonly form: FormGroup<{username: FormControl<string>}> = this._fb.nonNullable.group({
    username: ['', [Validators.required, Validators.email, Validators.maxLength(255)]]
  });
  readonly $isSubmitting: WritableSignal<boolean> = signal(false);
  readonly $message: WritableSignal<string> = signal('');
  readonly $errorMessage: WritableSignal<string> = signal('');

  submit(): void {
    if (this.$isSubmitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.$isSubmitting.set(true);
    this.$message.set('');
    this.$errorMessage.set('');
    this._passwordResetService.requestReset(this.form.controls.username.value)
      .pipe(takeUntilDestroyed(this._destroyRef), finalize((): void => this.$isSubmitting.set(false)))
      .subscribe({
        next: (response): void => this.$message.set(response.message),
        error: (error: HttpErrorResponse) => {
          this.$errorMessage.set('We could not process that request. Please try again shortly.');
          reportError('password-reset-request', error);
        }
      });
  }
}
