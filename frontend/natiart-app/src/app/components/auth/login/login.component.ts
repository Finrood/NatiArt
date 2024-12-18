import {Component, OnInit} from '@angular/core';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {HttpClient} from "@angular/common/http";
import {Router} from "@angular/router";
import {FormsModule} from "@angular/forms";
import {NgClass, NgIf} from "@angular/common";
import {Credentials} from "../../../models/credentials.model";
import {RedirectService} from "../../../service/redirect.service";
import {AuthenticationService} from "../../../service/authentication.service";
import {LoginResponse} from "../../../models/loginResponse.model";

@Component({
    selector: 'app-login',
    imports: [
        FormsModule,
        NgIf,
        NgClass
    ],
    templateUrl: './login.component.html',
    styleUrl: './login.component.css',
    animations: [
        trigger('fadeIn', [
            transition(':enter', [
                style({ opacity: 0, transform: 'translateY(10px)' }),
                animate('0.5s ease-out', style({ opacity: 1, transform: 'translateY(0)' })),
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
  emailFocused = false;
  passwordFocused = false;
  showPassword = false;
  isHovered = false;

  credentials: Credentials = {
    username: '',
    password: ''
  };
  errorMessage: string = '';

  constructor(
    private http: HttpClient,
    private router: Router,
    private authenticationService: AuthenticationService,
    private redirectService: RedirectService
  ) {
  }

  ngOnInit(): void {
    if (this.authenticationService.getAccessToken()) {
      this.authenticationService.getCurrentUser();
      this.redirectToSavedUrlOrDashboard();
    }
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  onSubmit() {
    this.authenticationService.login(this.credentials)
      .subscribe({
        next: (response: LoginResponse) => {
          this.authenticationService.setAccessToken(response.accessToken);
          this.authenticationService.setRefreshToken(response.refreshToken);
          this.authenticationService.getCurrentUser();
          this.redirectToSavedUrlOrDashboard();
        },
        error: () => {
          this.errorMessage = 'Invalid email or password. Please try again.';
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
}
