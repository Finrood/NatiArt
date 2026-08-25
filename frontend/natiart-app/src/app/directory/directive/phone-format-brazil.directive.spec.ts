import {ElementRef, Renderer2} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {PhoneFormatBrazilDirective} from './phone-format-brazil.directive';

describe('PhoneFormatBrazilDirective', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: ElementRef, useValue: {nativeElement: document.createElement('input')}},
        Renderer2,
      ]
    });
  });

  it('should be created', () => {
    const directive = TestBed.inject(PhoneFormatBrazilDirective);
    expect(directive).toBeTruthy();
  });
});
