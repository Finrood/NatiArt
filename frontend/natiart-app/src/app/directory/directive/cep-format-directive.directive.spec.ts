import {ElementRef, Renderer2} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {CepFormatDirective} from './cep-format-directive.directive';

describe('CepFormatDirective', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: ElementRef, useValue: {nativeElement: document.createElement('input')}},
        Renderer2,
      ]
    });
  });

  it('should be created', () => {
    const directive = TestBed.inject(CepFormatDirective);
    expect(directive).toBeTruthy();
  });
});
