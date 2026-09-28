import {FormControl} from '@angular/forms';

import {CustomCepValidators} from './CustomCepValidators';

describe('CustomCepValidators', () => {
  const validate = CustomCepValidators.validCep();

  it('accepts only eight digits or the display hyphen', () => {
    expect(validate(new FormControl('88010000'))).toBeNull();
    expect(validate(new FormControl('88010-000'))).toBeNull();
    expect(validate(new FormControl('abc88010000'))).toEqual({invalidCep: true});
    expect(validate(new FormControl('88010 000'))).toEqual({invalidCep: true});
    expect(validate(new FormControl(null))).toBeNull();
  });
});
