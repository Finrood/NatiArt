import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomPhoneValidators {
  static validPhone(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value.replace(/\D/g, ''); // Remove non-numeric characters
      if (value.length === 10 || value.length === 11) {
        return null;
      }
      return { pattern: true };
    };
  }
}
