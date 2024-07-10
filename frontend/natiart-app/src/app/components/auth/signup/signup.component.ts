import {Component, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CustomValidators} from './CustomValidators';
import {animate, style, transition, trigger} from '@angular/animations';
import {Router, RouterLink} from '@angular/router';
import {finalize} from 'rxjs/operators';
import {NgClass, NgIf} from '@angular/common';
import {SignupService} from "../../../service/signup.service";
import {UserRegistration} from "../../../models/user-registration.model";
import {Profile} from "../../../models/profile.model";

@Component({
  selector: 'app-signup',
  standalone: true,
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css'],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('300ms', style({ opacity: 1 })),
      ]),
    ]),
    trigger('slideInRight', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('300ms', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
    ]),
  ],
  imports: [
    RouterLink,
    NgClass,
    ReactiveFormsModule,
    NgIf,
  ],
})
export class SignupComponent implements OnInit {
  signupForm: FormGroup;
  currentStep = 1;
  errorMessage = '';
  isLoadingAddress = false;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private signupService: SignupService
  ) {
    this.signupForm = this.initForm();
  }

  ngOnInit(): void {}

  private initForm(): FormGroup {
    return this.fb.group({
      username: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, CustomValidators.passwordComplexity()]],
      confirmPassword: ['', Validators.required],
      profile: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        phone: ['', Validators.pattern('[0-9]*')],
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        zipCode: ['', Validators.required],
        street: ['', Validators.required],
        complement: [''],
      }),
    }, { validator: CustomValidators.passwordMatchValidator });
  }

  onNextStep(): void {
    if (this.signupForm.get('username')?.invalid || this.signupForm.get('password')?.invalid || this.signupForm.get('confirmPassword')?.invalid) {
      this.setErrorMessage('Please fill all required fields correctly.');
      return;
    }
    this.currentStep = 2;
    this.clearErrorMessage();
  }

  onPreviousStep(): void {
    this.currentStep = 1;
    this.clearErrorMessage();
  }

  onSubmit(): void {
    if (this.signupForm.invalid) {
      this.setErrorMessage('Please fill all required fields correctly.');
      return;
    }

    const formValue = this.signupForm.value;
    const userRegistration: UserRegistration = {
      username: formValue.username,
      password: formValue.password,
      profile: formValue.profile as Profile
    };

    this.signupService.registerUser(userRegistration)
      .subscribe({
        next: () => {
          this.router.navigate(['/login'])
            .then(() => {});
        },
        error: (error: any) => {
          this.setErrorMessage('Registration failed. Please try again.');
          console.error('Registration error:', error);
        }
      });
  }

  onZipCodeChange(): void {
    const zipCode = this.signupForm.get('profile.zipCode')?.value?.replace(/\D/g, '');
    if (zipCode?.length !== 8) {
      return;
    }

    this.isLoadingAddress = true;
    this.signupService.getAddressFromZipCode(zipCode)
      .pipe(finalize(() => this.isLoadingAddress = false))
      .subscribe({
        next: (data: any) => {
          this.signupForm.patchValue({
            profile: {
              street: data.logradouro,
              city: data.localidade,
              state: data.uf,
              country: "Brazil",
              complement: data.complemento,
            },
          });
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }
}
