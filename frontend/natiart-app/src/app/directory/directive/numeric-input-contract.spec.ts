import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {CpfFormatDirective} from './cpf-format-directive.directive';
import {PhoneFormatBrazilDirective} from './phone-format-brazil.directive';
import {CepFormatDirective} from './cep-format-directive.directive';
import {CustomCepValidators} from '../validator/CustomCepValidators';
import {CustomCpfValidators} from '../validator/CustomCpfValidators';
import {CustomPhoneValidators} from '../validator/CustomPhoneValidators';

@Component({imports: [ReactiveFormsModule, CpfFormatDirective, PhoneFormatBrazilDirective, CepFormatDirective],
  template: `<form [formGroup]="form"><input cpfFormat formControlName="cpf"><input appPhoneFormatBrazil formControlName="phone">
    <input cepFormat formControlName="cep"></form>`})
class NumericInputs {
  readonly form = new FormGroup({cpf: new FormControl('', CustomCpfValidators.validCpf()),
    phone: new FormControl('', CustomPhoneValidators.validPhone()), cep: new FormControl('', CustomCepValidators.validCep())});
}

describe('Rendered numeric input contract', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [NumericInputs]}).compileComponents();});
  it('preserves Control/Meta clipboard, select-all and undo shortcuts while blocking non-digits', () => {
    const fixture = TestBed.createComponent(NumericInputs);
    fixture.detectChanges();
    for (const selector of ['input[cpfFormat]', 'input[appPhoneFormatBrazil]']) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(selector);
      for (const modifier of ['ctrlKey', 'metaKey']) {
        for (const key of ['a', 'c', 'v', 'x', 'z', 'y']) {
          const event = new KeyboardEvent('keydown', {key, [modifier]: true, bubbles: true, cancelable: true});
          input.dispatchEvent(event);
          expect(event.defaultPrevented).withContext(modifier + ':' + key).toBeFalse();
        }
      }
      for (const key of ['f', ' ']) {
        const event = new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true});
        input.dispatchEvent(event);
        expect(event.defaultPrevented).toBeTrue();
      }
    }
    fixture.destroy();
  });

  it('normalizes already formatted CPF/phone paste and safely resets optional validators', () => {
    const fixture = TestBed.createComponent(NumericInputs);
    fixture.detectChanges();
    for (const [name, formatted, normalized] of [['cpf', '529.982.247-25', '52998224725'],
      ['phone', '(11) 99999-9999', '11999999999']]) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[formControlName="${name}"]`);
      input.value = formatted;
      input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertFromPaste', data: formatted}));
      expect(fixture.componentInstance.form.get(name)!.value).toBe(normalized);
      expect(input.value).toBe(formatted);
    }
    expect(fixture.componentInstance.form.valid).toBeTrue();
    expect(() => fixture.componentInstance.form.reset()).not.toThrow();
    expect(fixture.componentInstance.form.valid).toBeTrue();
    fixture.destroy();
  });
});
