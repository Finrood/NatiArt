import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { PersonalizationModalComponent } from './personalization-modal.component';

describe('PersonalizationModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonalizationModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
