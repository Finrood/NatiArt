import { Renderer2 } from '@angular/core';
import { NgControl } from '@angular/forms';

import { CpfFormatDirective } from './cpf-format-directive.directive';

describe('CpfFormatDirective', () => {
  it('should be created', () => {
    const renderer = {} as Renderer2;
    const control = { control: null } as unknown as NgControl;
    const directive = new CpfFormatDirective(renderer, control);
    expect(directive).toBeTruthy();
  });
});
