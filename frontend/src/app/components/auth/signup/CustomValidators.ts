import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export class CustomValidators {
  static passwordComplexity(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) {
        return null; // Don't validate empty values to allow optional controls
      }

      const isValid = [
        /[A-Z]/.test(value),  // has uppercase letter
        /[a-z]/.test(value),  // has lowercase letter
        /[0-9]/.test(value),  // has numeric digit
        value.length >= 6     // is valid length
      ].every(Boolean);

      return isValid ? null : { passwordComplexity: true };
    };
  }

  static passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const password = control.get('password');
    const confirmPassword = control.get('confirmPassword');

    if (!password || !confirmPassword) {
      return null; // No error if one of the controls is missing
    }

    return password.value === confirmPassword.value ? null : { passwordMismatch: true };
  };
}
