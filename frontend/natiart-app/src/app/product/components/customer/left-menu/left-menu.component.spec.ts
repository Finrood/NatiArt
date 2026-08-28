import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { LeftMenuComponent } from './left-menu.component';

describe('LeftMenuComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeftMenuComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LeftMenuComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
