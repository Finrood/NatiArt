import {Component, provideZonelessChangeDetection} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { NatiartFormFieldComponent } from './natiart-form-field.component';

describe('NatiartFormFieldComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NatiartFormFieldComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(NatiartFormFieldComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});

@Component({imports: [NatiartFormFieldComponent, ReactiveFormsModule], template: `
  <form [formGroup]="form"><app-natiart-form-field label="Parcel height" controlName="height" [form]="form">
    <input type="number" formControlName="height">
  </app-natiart-form-field><app-natiart-form-field label="Name" controlName="name" [form]="form">
    <input type="text" formControlName="name">
  </app-natiart-form-field></form>`})
class BoundaryFields {
  readonly form: FormGroup = new FormGroup({
    height: new FormControl(10, [Validators.min(0.01), Validators.max(200)]),
    name: new FormControl('Art', Validators.maxLength(5))
  });
}

describe('Form field boundary feedback', () => {
  it('renders numeric and text limits, associates them with their fields and clears corrected errors', async () => {
    await TestBed.configureTestingModule({imports: [BoundaryFields], providers: [provideZonelessChangeDetection()]}).compileComponents();
    const fixture = TestBed.createComponent(BoundaryFields); await fixture.whenStable();
    const host: HTMLElement = fixture.nativeElement;
    const input: HTMLInputElement = host.querySelector('input[type="number"]')!;
    for (const [value, expected] of [[0, '0.01 or greater'], [201, '200 or less']] as const) {
      input.value = String(value); input.dispatchEvent(new Event('input')); await fixture.whenStable();
      const id: string = input.getAttribute('aria-describedby')!;
      expect(host.querySelector('[id="' + id + '"]')!.textContent).toContain(expected);
      expect(input.getAttribute('aria-invalid')).toBe('true');
    }
    input.value = '10'; input.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('false');
    expect(input.getAttribute('aria-describedby')).toBeNull();
    const text: HTMLInputElement = host.querySelector('input[type="text"]')!;
    text.value = 'Long name'; text.dispatchEvent(new Event('input')); await fixture.whenStable();
    expect(host.textContent).toContain('Use at most 5 characters');
    expect(text.getAttribute('aria-describedby')).not.toBeNull();
    fixture.destroy();
  });
});
