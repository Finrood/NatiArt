import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomCpfValidators {
  static validCpf(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value.replace(/\D/g, ''); // Remove non-numeric characters
      if (value.length === 11) {
        return null;
      }
      return { pattern: true };
    };
  }
}
