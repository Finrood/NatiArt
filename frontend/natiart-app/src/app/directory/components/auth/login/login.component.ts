import {Component, OnInit} from '@angular/core';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {Router} from "@angular/router";
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from "@angular/forms";

import {RedirectService} from "../../../service/redirect.service";
import {AuthenticationService} from "../../../service/authentication.service";
import {User} from "../../../models/user.model";
import {NatiartFormFieldComponent} from "../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {Credentials} from "../../../models/credentials.model";
import {TokenService} from "../../../service/token.service";

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    ButtonComponent
],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({opacity: 0, transform: 'translateY(10px)'}),
        animate('0.5s ease-out', style({opacity: 1, transform: 'translateY(0)'})),
      ]),
    ]),
    trigger('inputFocus', [
      state('unfocused', style({
        transform: 'scale(1)',
        boxShadow: 'none'
      })),
      state('focused', style({
        transform: 'scale(1.02)',
        boxShadow: '0 0 0 2px rgba(212, 165, 154, 0.2)'
      })),
      transition('unfocused <=> focused', [
        animate('0.2s ease-in-out')
      ])
    ]),
    trigger('buttonHover', [
      state('unhovered', style({
        transform: 'scale(1)'
      })),
      state('hovered', style({
        transform: 'scale(1.05)'
      })),
      transition('unhovered <=> hovered', [
        animate('0.2s ease-in-out')
      ])
    ])
  ],
  styles: []
})
export class LoginComponent implements OnInit {
  showPassword = false;
  loginForm: FormGroup;
  errorMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private authenticationService: AuthenticationService,
    private tokenService: TokenService,
    private redirectService: RedirectService
  ) {
    this.loginForm = this.initForm();
  }

  ngOnInit(): void {
    if (this.tokenService.accessToken) {
      this.authenticationService.fetchCurrentUser();
      this.redirectToSavedUrlOrDashboard();
    }
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  doLoginUser() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.setErrorMessage('Please fill all required fields correctly.');
      return;
    }

    const formValue = this.loginForm.getRawValue();
    const credentials: Credentials = {
      username: formValue.credentials.username,
      password: formValue.credentials.password,
    };

    this.authenticationService.login(credentials)
      .subscribe({
        next: (user: User) => {
          this.clearErrorMessage();
          this.redirectToSavedUrlOrDashboard();
        },
        error: (error: any) => {
          this.setErrorMessage('Invalid email or password. Please try again.');
          console.error('Login error:', error);
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
