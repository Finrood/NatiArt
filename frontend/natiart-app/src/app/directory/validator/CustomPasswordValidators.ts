import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';
import {checkPasswordRequirements, DEFAULT_REQUIREMENTS} from '../utils/password-utils';

export class CustomPasswordValidators {
  static passwordComplexity(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) {
        return null; // Don't validate empty values to allow optional controls
      }

      const checks = checkPasswordRequirements(value, DEFAULT_REQUIREMENTS);
      const isValid = Object.values(checks).every(Boolean);

      return isValid ? null : {passwordComplexity: true};
    };
  }

  static passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const password = control.get('password');
    const confirmPassword = control.get('confirmPassword');

    if (!password || !confirmPassword) {
      return null; // No error if one of the controls is missing
    }

    return password.value === confirmPassword.value ? null : {passwordMismatch: true};
  };
}
