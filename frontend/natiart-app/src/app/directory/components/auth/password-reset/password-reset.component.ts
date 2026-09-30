import {Component, DestroyRef, inject, OnInit, signal, WritableSignal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors} from '@angular/forms';
import {finalize} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

import {ButtonComponent} from '../../../../shared/components/button.component';
import {NatiartFormFieldComponent} from '../../../../shared/components/natiart-form-field/natiart-form-field.component';
import {PasswordRequirementsComponent} from '../signup/password-requirements/password-requirements.component';
import {CustomPasswordValidators} from '../../../validator/CustomPasswordValidators';
import {PasswordResetService} from '../../../service/password-reset.service';
import {AuthenticationService} from '../../../service/authentication.service';
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-password-reset',
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, NatiartFormFieldComponent, PasswordRequirementsComponent],
  templateUrl: './password-reset.component.html'
})
export class PasswordResetComponent implements OnInit {
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _route: ActivatedRoute = inject(ActivatedRoute);
  private readonly _router: Router = inject(Router);
  private readonly _passwordResetService: PasswordResetService = inject(PasswordResetService);
  private readonly _authenticationService: AuthenticationService = inject(AuthenticationService);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);
  readonly form: FormGroup<{password: FormControl<string>; passwordConfirmation: FormControl<string>}> = this._fb.nonNullable.group({
    password: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]],
    passwordConfirmation: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]]
  }, {validators: PasswordResetComponent.passwordMatchValidator});
  private _token: string = '';
  readonly $isSubmitting: WritableSignal<boolean> = signal(false);
  readonly $success: WritableSignal<boolean> = signal(false);
  readonly $errorMessage: WritableSignal<string> = signal('');

  ngOnInit(): void {
    const fragment: string = this._route.snapshot.fragment ?? '';
    const values: string[] = new URLSearchParams(fragment).getAll('token');
    this._token = values.length === 1 && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(values[0]) ? values[0] : '';
    if (fragment) {
      void this._router.navigate([], {relativeTo: this._route, fragment: undefined, replaceUrl: true});
    }
    this._destroyRef.onDestroy((): void => {
      this._token = '';
      this.form.reset(undefined, {emitEvent: false});
    });
  }

  submit(): void {
    if (this.$isSubmitting()) {
      return;
    }
    if (!this._token) {
      this.$errorMessage.set('This reset link is missing or invalid. Please request a new one.');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.$isSubmitting.set(true);
    this.$errorMessage.set('');
    const {password, passwordConfirmation} = this.form.getRawValue();
    this._passwordResetService.resetPassword(this._token, password, passwordConfirmation)
      .pipe(takeUntilDestroyed(this._destroyRef), finalize((): void => this.$isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.$success.set(true);
          this._token = '';
          this.form.reset();
          this._authenticationService.resetAuthStateAndRedirect();
          void this._router.navigate(['/login']);
        },
        error: (error: HttpErrorResponse) => {
          this.$errorMessage.set('This reset link is invalid or expired. Please request a new one.');
          reportError('password-reset', error);
        }
      });
  }

  private static readonly passwordMatchValidator = (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('password')?.value;
    const confirmation = group.get('passwordConfirmation')?.value;
    return password === confirmation ? null : {passwordMismatch: true};
  };
}
