import {Component, OnInit} from '@angular/core';
import { trigger, state, style, animate, transition } from '@angular/animations';
import {HttpClient} from "@angular/common/http";
import {Router} from "@angular/router";
import {FormsModule} from "@angular/forms";
import {NgIf} from "@angular/common";
import {TokenService} from "../../../service/token.service";
import {Credentials} from "../../../models/credentials.model";

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule,
    NgIf
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
  isHovered = false;

  credentials: Credentials = {
    username: '',
    password: ''
  };
  errorMessage: string = '';

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {}

  ngOnInit(): void {
    if (this.tokenService.getAccessToken()) {
      this.router.navigate(['/dashboard'])
        .then(() => {});
    }
  }

  onSubmit() {
    this.http.post<{ accessToken: string, refreshToken: string }>('http://localhost:8081/login', this.credentials)
      .subscribe({
        next: (response) => {
          this.tokenService.setAccessToken(response.accessToken);
          this.tokenService.setRefreshToken(response.refreshToken);
          this.router.navigate(['/dashboard'])
            .then(() => {});
        },
        error: () => {
          this.errorMessage = 'Invalid email or password. Please try again.';
        }
      });
  }
}