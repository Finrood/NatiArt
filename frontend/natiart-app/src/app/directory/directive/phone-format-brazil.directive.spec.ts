import { Renderer2 } from '@angular/core';
import {TestBed} from '@angular/core/testing';
import { NgControl } from '@angular/forms';

import { PhoneFormatBrazilDirective } from './phone-format-brazil.directive';

describe('PhoneFormatBrazilDirective', () => {
  it('should be created', () => {
    const renderer = {} as Renderer2;
    const control = { control: null } as unknown as NgControl;
    TestBed.configureTestingModule({providers: [{provide: Renderer2, useValue: renderer}, {provide: NgControl, useValue: control}]});
    const directive = TestBed.runInInjectionContext(() => new PhoneFormatBrazilDirective());
    expect(directive).toBeTruthy();
  });
});
