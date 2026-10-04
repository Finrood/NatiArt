import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomCepValidators {
  static validCep(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const cep: unknown = control.value;
      if (cep === null || cep === '') {
        return null; // Don't validate empty or null values, use Validators.required for that
      }
      return typeof cep === 'string' && /^(?:[0-9]{8}|[0-9]{5}-[0-9]{3})$/.test(cep)
        ? null
        : { invalidCep: true };
    };
  }
}
