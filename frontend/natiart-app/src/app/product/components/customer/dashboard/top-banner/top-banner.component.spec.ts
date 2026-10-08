import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {TopBannerComponent} from './top-banner.component';

describe('TopBannerComponent', () => {
  it('should create', async () => {
    await TestBed.configureTestingModule({imports: [TopBannerComponent], providers: [provideRouter([])]}).compileComponents();
    const fixture = TestBed.createComponent(TopBannerComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
