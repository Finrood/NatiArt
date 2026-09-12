import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { TopBannerComponent } from './top-banner.component';

describe('TopBannerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopBannerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  const createComponent = (): ComponentFixture<TopBannerComponent> => {
    const fixture = TestBed.createComponent(TopBannerComponent);
    fixture.detectChanges();
    return fixture;
  };

  it('should create', () => {
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('keepsTheReviewedBannerAfterOneFullRotationInterval', fakeAsync(() => {
    const fixture = createComponent();
    expect(fixture.componentInstance.currentBannerIndex).toBe(0);

    tick(4500);

    expect(fixture.componentInstance.currentBannerIndex).toBe(0);
  }));

  it('nextSlideWrapsPastTheLastBannerBackToTheFirst', () => {
    const fixture = createComponent();
    fixture.componentInstance.currentBannerIndex = 0;

    fixture.componentInstance.nextSlide();

    expect(fixture.componentInstance.currentBannerIndex).toBe(0);
  });

  it('prevSlideWrapsBackToTheLastBannerFromTheFirst', () => {
    const fixture = createComponent();
    fixture.componentInstance.currentBannerIndex = 0;

    fixture.componentInstance.prevSlide();

    expect(fixture.componentInstance.currentBannerIndex).toBe(0);
  });

  it('goToSlideJumpsToTheRequestedBannerAndRestartsTheRotation', fakeAsync(() => {
    const fixture = createComponent();

    fixture.componentInstance.goToSlide(0);
    expect(fixture.componentInstance.currentBannerIndex).toBe(0);

    tick(4500);

    expect(fixture.componentInstance.currentBannerIndex).toBe(0);
  }));

  it('ngOnDestroyStopsTheRotationForGood', fakeAsync(() => {
    const fixture = createComponent();
    fixture.destroy();
    const indexAtDestroy = fixture.componentInstance.currentBannerIndex;

    tick(9000);

    expect(fixture.componentInstance.currentBannerIndex).toBe(indexAtDestroy);
  }));
});
