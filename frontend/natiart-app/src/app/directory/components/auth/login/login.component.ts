import {Component, OnInit} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {Router} from "@angular/router";
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from "@angular/forms";

import {RedirectService} from "../../../service/redirect.service";
import {AuthenticationService} from "../../../service/authentication.service";
import {User} from "../../../models/user.model";
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
  showPassword = false;
  loginForm: FormGroup;
  errorMessage: string = '';
  isSubmitting: boolean = false;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authenticationService: AuthenticationService,
    private tokenService: TokenService,
    private redirectService: RedirectService
  ) {
    this.loginForm = this.initForm();
  }

  get credentialsForm(): FormGroup {
    return this.loginForm.get('credentials') as FormGroup;
  }

  ngOnInit(): void {
    if (this.tokenService.accessToken) {
      this.authenticationService.fetchCurrentUser()
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

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  doLoginUser() {
    if (this.isSubmitting) {
      return;
    }
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.setErrorMessage('Please fill all required fields correctly.');
      return;
    }

    const credentials = this.credentialsForm.value;
    this.isSubmitting = true;

    this.authenticationService.login(credentials)
      .pipe(finalize(() => this.isSubmitting = false))
      .subscribe({
        next: (user: User) => {
          this.clearErrorMessage();
          this.redirectToSavedUrlOrDashboard();
        },
        error: (error: HttpErrorResponse) => {
          this.setErrorMessage('Invalid email or password. Please try again.');
          reportError('login', error);
        }
      });
  }

  private redirectToSavedUrlOrDashboard() {
    const redirectUrl = this.redirectService.getRedirectUrl();
    if (redirectUrl) {
      this.router.navigateByUrl(redirectUrl)
        .then(() => {
        });
    } else {
      this.router.navigate(['/dashboard'])
        .then(() => {
        });
    }
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }

  private initForm(): FormGroup {
    return this.fb.group({
      credentials: this.fb.group({
        username: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required]]
      })
    });
  }
}
