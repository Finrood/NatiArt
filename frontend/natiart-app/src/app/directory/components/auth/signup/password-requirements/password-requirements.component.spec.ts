import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { PasswordRequirementsComponent } from './password-requirements.component';

describe('PasswordRequirementsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PasswordRequirementsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PasswordRequirementsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
  it('shows neutral requirements for empty/reset values and checks only an entered password', () => {
    const fixture = TestBed.createComponent(PasswordRequirementsComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('svg').length).toBe(0);
    expect(fixture.nativeElement.textContent).not.toContain('UTF-8');
    fixture.componentRef.setInput('password', 'Abc12345');
    fixture.detectChanges();
    expect(fixture.componentInstance.requirementsList.every(requirement => requirement.valid)).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('svg').length).toBe(4);
    fixture.componentRef.setInput('password', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('svg').length).toBe(0);
    fixture.destroy();
  });

});
