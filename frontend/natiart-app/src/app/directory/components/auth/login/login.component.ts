import {Component, DestroyRef, OnInit, inject, signal, WritableSignal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {Router} from "@angular/router";
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from "@angular/forms";

import {RedirectService} from "../../../service/redirect.service";
import {AuthenticationService} from "../../../service/authentication.service";
import {NatiartFormFieldComponent} from "../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {Credentials} from "../../../models/credentials.model";
import {TokenService} from "../../../service/token.service";
import {RouterLink} from "@angular/router";
import {reportError} from '../../../../shared/service/error-reporting.service';
import {finalize} from "rxjs/operators";

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    ButtonComponent,
    RouterLink
],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  styles: []
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  readonly $errorMessage: WritableSignal<string> = signal('');
  readonly $isSubmitting: WritableSignal<boolean> = signal(false);

  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _router: Router = inject(Router);
  private readonly _authenticationService: AuthenticationService = inject(AuthenticationService);
  private readonly _tokenService: TokenService = inject(TokenService);
  private readonly _redirectService: RedirectService = inject(RedirectService);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);

  constructor() {
    this.loginForm = this.initForm();
  }

  get credentialsForm(): FormGroup {
    return this.loginForm.get('credentials') as FormGroup;
  }

  ngOnInit(): void {
    if (this._tokenService.accessToken) {
      this._authenticationService.fetchCurrentUser()
        .pipe(takeUntilDestroyed(this._destroyRef))
        .subscribe({
          next: () => this.redirectToSavedUrlOrDashboard(),
          error: () => {
            // 401/403 clearing is owned by AuthenticationService
            // (resetAuthStateAndRedirect clears and stays on /login); any
            // other failure (network blip, 5xx) must not wipe stored
            // credentials — the session stays intact for a retry.
          },
        });
    }
  }

  doLoginUser(): void {
    if (this.$isSubmitting()) {
      return;
    }
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.setErrorMessage($localize`Please fill all required fields correctly.`);
      return;
    }

    const credentials: Credentials = this.credentialsForm.value;
    this.clearErrorMessage();
    this.$isSubmitting.set(true);

    this._authenticationService.login(credentials)
      .pipe(
        takeUntilDestroyed(this._destroyRef),
        finalize((): void => this.$isSubmitting.set(false))
      )
      .subscribe({
        next: (): void => {
          this.clearErrorMessage();
          this.redirectToSavedUrlOrDashboard();
        },
        error: (error: HttpErrorResponse) => {
          this.setErrorMessage($localize`Invalid email or password. Please try again.`);
          reportError('login', error);
        }
      });
  }

  private redirectToSavedUrlOrDashboard(): void {
    const redirectUrl = this._redirectService.getRedirectUrl();
    if (redirectUrl) {
      this._router.navigateByUrl(redirectUrl)
        .then(() => {
        });
    } else {
      this._router.navigate(['/dashboard'])
        .then(() => {
        });
    }
  }

  private setErrorMessage(message: string): void {
    this.$errorMessage.set(message);
  }

  private clearErrorMessage(): void {
    this.$errorMessage.set('');
  }

  private initForm(): FormGroup {
    return this._fb.group({
      credentials: this._fb.group({
        username: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required]]
      })
    });
  }
}
