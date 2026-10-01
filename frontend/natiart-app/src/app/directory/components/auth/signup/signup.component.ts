import {ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CustomPasswordValidators} from '../../../validator/CustomPasswordValidators';
import {Router, RouterLink} from '@angular/router';
import {CommonModule} from "@angular/common";

import {SignupService} from "../../../service/signup.service";
import {UserRegistration} from "../../../models/user-registration.model";
import {Profile} from "../../../models/profile.model";
import {SignupProfileComponent} from "./signup-profile/signup-profile.component";
import {SignupCredentialsComponent} from "./signup-credentials/signup-credentials.component";
import {StepIndicatorComponent} from "./step-indicator/step-indicator.component";
import {CustomPhoneValidators} from "../../../validator/CustomPhoneValidators";
import {CustomCpfValidators} from "../../../validator/CustomCpfValidators";
import {CustomCepValidators} from "../../../validator/CustomCepValidators";
import {reportError} from '../../../../shared/service/error-reporting.service';
import {finalize} from 'rxjs/operators';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    SignupProfileComponent,
    SignupCredentialsComponent,
    StepIndicatorComponent,
    CommonModule
]
})
export class SignupComponent implements OnInit {
  signupForm: FormGroup;
  currentStep = 1;
  errorMessage = '';
  isSubmitting = false;

  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _router: Router = inject(Router);
  private readonly _signupService: SignupService = inject(SignupService);

  private readonly _changeDetectorRef: ChangeDetectorRef = inject(ChangeDetectorRef);

  constructor() {
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
    if (this.isSubmitting) {
      return;
    }
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      this.setErrorMessage($localize`Please fill all required fields correctly.`);
      return;
    }

    const formValue = this.signupForm.getRawValue();
    const userRegistration: UserRegistration = {
      username: formValue.credentials.username,
      password: formValue.credentials.password,
      profile: formValue.profile as Profile
    };

    this.isSubmitting = true;
    this._signupService.registerUser(userRegistration)
      .pipe(finalize(() => {
        this.isSubmitting = false;
        this._changeDetectorRef.markForCheck();
      }))
      .subscribe({
        next: () => {
          this._router.navigate(['/login'])
            .then(() => {
            });
        },
        error: (error: HttpErrorResponse) => {
          this.setErrorMessage(this.registrationErrorMessage(error));
          reportError('registration', error);
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
    return this._fb.group({
      credentials: this._fb.group({
        username: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, CustomPasswordValidators.passwordComplexity()]],
        confirmPassword: ['', Validators.required],
      }, {validators: CustomPasswordValidators.passwordMatchValidator}),
      profile: this._fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, CustomCpfValidators.validCpf()]],
        phone: ['', [CustomPhoneValidators.validPhone()]],
        country: ['Brazil', Validators.required],
        state: ['', [Validators.required, Validators.pattern(/^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$/i)]],
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
    this._changeDetectorRef.markForCheck();
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
    this._changeDetectorRef.markForCheck();
  }

  private registrationErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 409) {
      return $localize`An account with this email already exists.`;
    }
    if (error.status === 0) {
      return $localize`The service is unavailable. Please try again.`;
    }
    return $localize`Registration failed. Please try again.`;
  }
}
