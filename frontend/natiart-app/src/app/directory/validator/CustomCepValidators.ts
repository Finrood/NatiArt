import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomCepValidators {
  static validCep(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const cep = typeof control.value === 'string' ? control.value.replace(/\D/g, '') : '';

      if (cep.length === 0) {
        return null; // Don't validate empty or null values, use Validators.required for that
      }

      if (cep.length !== 8) {
        return { invalidCep: true };
      }

      return null; // CEP is valid
    };
  }
}
