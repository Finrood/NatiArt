import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {CustomCpfValidators} from '../../directory/validator/CustomCpfValidators';
import {CustomCepValidators} from '../../directory/validator/CustomCepValidators';
import {CustomPhoneValidators} from '../../directory/validator/CustomPhoneValidators';

export function createProfileForm(fb: FormBuilder): FormGroup {
  return fb.nonNullable.group({
    firstname: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    lastname: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    cpf: ['', [Validators.required, CustomCpfValidators.validCpf()]],
    phone: ['', CustomPhoneValidators.validPhone()],
    ...createAddressForm(fb).controls
  });
}

export function createAddressForm(fb: FormBuilder): FormGroup {
  return fb.nonNullable.group({
    country: ['Brazil', [Validators.required, Validators.maxLength(100)]],
    state: ['', [Validators.required, Validators.pattern(/^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$/i)]],
    city: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    neighborhood: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    zipCode: ['', [Validators.required, CustomCepValidators.validCep()]],
    street: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    houseNumber: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    complement: ['', Validators.maxLength(255)]
  });
}
