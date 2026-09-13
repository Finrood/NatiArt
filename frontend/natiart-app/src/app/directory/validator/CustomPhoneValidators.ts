import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomPhoneValidators {
  static validPhone(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = typeof control.value === 'string' ? control.value.replace(/\D/g, '') : '';
      if (value.length === 0) {
        return null;
      }
      if (value.length === 10 || value.length === 11) {
        return null;
      }
      return { pattern: true };
    };
  }
}
