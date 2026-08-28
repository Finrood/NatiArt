import { Renderer2 } from '@angular/core';
import { NgControl } from '@angular/forms';

import { PhoneFormatBrazilDirective } from './phone-format-brazil.directive';

describe('PhoneFormatBrazilDirective', () => {
  it('should be created', () => {
    const renderer = {} as Renderer2;
    const control = { control: null } as unknown as NgControl;
    const directive = new PhoneFormatBrazilDirective(renderer, control);
    expect(directive).toBeTruthy();
  });
});
