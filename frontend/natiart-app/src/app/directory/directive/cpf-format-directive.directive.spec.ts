import {ElementRef, Renderer2} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {CpfFormatDirective} from './cpf-format-directive.directive';

describe('CpfFormatDirective', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: ElementRef, useValue: {nativeElement: document.createElement('input')}},
        Renderer2,
      ]
    });
  });

  it('should be created', () => {
    const directive = TestBed.inject(CpfFormatDirective);
    expect(directive).toBeTruthy();
  });
});
