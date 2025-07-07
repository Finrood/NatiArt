import {AbstractControl, ValidationErrors, ValidatorFn} from '@angular/forms';

export class CustomCpfValidators {
  static validCpf(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const cpf = control.value.replace(/\D/g, ''); // Remove non-numeric characters

      if (cpf === null || cpf.length === 0) {
        return null; // Don't validate empty or null values, use Validators.required for that
      }

      if (cpf.length !== 11 || !/^[0-9]{11}$/.test(cpf)) {
        return { invalidCpf: true };
      }

      // Check for sequencial numbers (e.g., 111.111.111-11)
      if (/^(\d)\1{10}$/.test(cpf)) {
        return { invalidCpf: true };
      }

      let sum = 0;
      let remainder;

      // Validate first digit
      for (let i = 1; i <= 9; i++) {
        sum = sum + parseInt(cpf.substring(i - 1, i)) * (11 - i);
      }
      remainder = (sum * 10) % 11;

      if ((remainder === 10) || (remainder === 11)) {
        remainder = 0;
      }
      if (remainder !== parseInt(cpf.substring(9, 10))) {
        return { invalidCpf: true };
      }

      sum = 0;
      // Validate second digit
      for (let i = 1; i <= 10; i++) {
        sum = sum + parseInt(cpf.substring(i - 1, i)) * (12 - i);
      }
      remainder = (sum * 10) % 11;

      if ((remainder === 10) || (remainder === 11)) {
        remainder = 0;
      }
      if (remainder !== parseInt(cpf.substring(10, 11))) {
        return { invalidCpf: true };
      }

      return null; // CPF is valid
    };
  }
}