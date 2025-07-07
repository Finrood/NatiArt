import {ChangeDetectionStrategy, Component, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CustomPasswordValidators} from '../../../validator/CustomPasswordValidators';
import {animate, style, transition, trigger} from '@angular/animations';
import {Router, RouterLink} from '@angular/router';
import {NgIf} from '@angular/common';
import {SignupService} from "../../../service/signup.service";
import {UserRegistration} from "../../../models/user-registration.model";
import {Profile} from "../../../models/profile.model";
import {SignupProfileComponent} from "./signup-profile/signup-profile.component";
import {SignupCredentialsComponent} from "./signup-credentials/signup-credentials.component";
import {StepIndicatorComponent} from "./step-indicator/step-indicator.component";
import {CustomPhoneValidators} from "../../../validator/CustomPhoneValidators";
import {CustomCpfValidators} from "../../../validator/CustomCpfValidators";
import {CustomCepValidators} from "../../../validator/CustomCepValidators";

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('300ms ease-out', style({ opacity: 1 })),
      ]),
    ]),
    trigger('slideIn', [
      transition('* <=> *', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('400ms cubic-bezier(0.4, 0, 0.2, 1)', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
    ]),
  ],
  imports: [
    ReactiveFormsModule,
    NgIf,
    RouterLink,
    SignupProfileComponent,
    SignupCredentialsComponent,
    StepIndicatorComponent,

  ]
})
export class SignupComponent implements OnInit {
  signupForm: FormGroup;
  currentStep = 1;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private signupService: SignupService
  ) {
    this.signupForm = this.initForm();
  }

  ngOnInit(): void {
  }

  onNextStep(): void {
    if (this.signupForm.get('credentials')?.invalid) {
      this.signupForm.get('credentials')?.markAllAsTouched();
      return;
    }
    this.currentStep = 2;
    this.clearErrorMessage();
  }

  onPreviousStep(): void {
    this.currentStep = 1;
    this.clearErrorMessage();
  }

  doRegisterUser(): void {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      this.setErrorMessage('Please fill all required fields correctly.');
      return;
    }

    const formValue = this.signupForm.getRawValue();
    const userRegistration: UserRegistration = {
      username: formValue.credentials.username,
      password: formValue.credentials.password,
      profile: formValue.profile as Profile
    };

    this.signupService.registerUser(userRegistration)
      .subscribe({
        next: () => {
          this.router.navigate(['/login'])
            .then(() => {
            });
        },
        error: (error: any) => {
          this.setErrorMessage('Registration failed. Please try again.');
          console.error('Registration error:', error);
        }
      });
  }

  get credentialsForm(): FormGroup {
    return this.signupForm.get('credentials') as FormGroup;
  }

  get profileForm(): FormGroup {
    return this.signupForm.get('profile') as FormGroup;
  }

  private initForm(): FormGroup {
    return this.fb.group({
      credentials: this.fb.group({
        username: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]],
        confirmPassword: ['', Validators.required],
      }, {validators: CustomPasswordValidators.passwordMatchValidator}),
      profile: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, CustomCpfValidators.validCpf()]],
        phone: ['', [CustomPhoneValidators.validPhone()]],
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', [Validators.required, CustomCepValidators.validCep()]],
        street: ['', Validators.required],
        complement: [''],
      }),
    });
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }

  protected readonly FormGroup = FormGroup;
}
