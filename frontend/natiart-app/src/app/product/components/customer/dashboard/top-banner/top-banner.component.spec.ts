import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { TopBannerComponent } from './top-banner.component';

describe('TopBannerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopBannerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(TopBannerComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
