import { Renderer2 } from '@angular/core';
import { NgControl } from '@angular/forms';

import { CepFormatDirective } from './cep-format-directive.directive';

describe('CepFormatDirective', () => {
  it('should be created', () => {
    const renderer = {} as Renderer2;
    const control = { control: null } as unknown as NgControl;
    const directive = new CepFormatDirective(renderer, control);
    expect(directive).toBeTruthy();
  });
});
