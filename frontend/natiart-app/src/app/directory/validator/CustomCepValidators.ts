import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomCepValidators {
  static validCep(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const cep = control.value.replace(/\D/g, ''); // Remove non-numeric characters

      if (cep === null || cep.length === 0) {
        return null; // Don't validate empty or null values, use Validators.required for that
      }

      if (cep.length !== 8) {
        return { invalidCep: true };
      }

      return null; // CEP is valid
    };
  }
}
