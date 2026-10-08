import {Component, DestroyRef, inject, OnDestroy, signal, WritableSignal} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {AccountService, accountErrorMessage} from '../../service/account.service';
import {TokenService} from '../../service/token.service';
import {CustomPasswordValidators} from '../../validator/CustomPasswordValidators';
import {PasswordRequirementsComponent} from '../auth/signup/password-requirements/password-requirements.component';
import {NatiartFormFieldComponent} from '../../../shared/components/natiart-form-field/natiart-form-field.component';

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, RouterLink, PasswordRequirementsComponent, NatiartFormFieldComponent],
  templateUrl: './change-password.component.html'
})
export class ChangePasswordComponent implements OnDestroy {
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _accounts: AccountService = inject(AccountService);
  private readonly _tokens: TokenService = inject(TokenService);
  private readonly _destroy: DestroyRef = inject(DestroyRef);
  readonly form: FormGroup = this._fb.nonNullable.group({
    currentPassword: ['', [Validators.required, Validators.maxLength(256)]],
    password: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]],
    confirmPassword: ['', Validators.required]
  }, {validators: CustomPasswordValidators.passwordMatchValidator});
  readonly $saving: WritableSignal<boolean> = signal(false);
  readonly $done: WritableSignal<boolean> = signal(false);
  readonly $error: WritableSignal<string> = signal('');

  save(): void {
    if (this.$saving() || this.$done()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.$saving.set(true);
    this.$error.set('');
    const value: {currentPassword: string; password: string; confirmPassword: string} = this.form.getRawValue();
    this._accounts.changePassword(value.currentPassword, value.password, value.confirmPassword)
      .pipe(takeUntilDestroyed(this._destroy), finalize((): void => this.$saving.set(false)))
      .subscribe({
        next: (): void => {
          this.form.reset();
          this._tokens.clearTokens();
          this.$done.set(true);
        },
        error: (error: HttpErrorResponse): void => {
          this.form.get('currentPassword')!.reset();
          this.$error.set(accountErrorMessage(error));
        }
      });
  }
  ngOnDestroy(): void {
    this.form.reset();
  }
}
