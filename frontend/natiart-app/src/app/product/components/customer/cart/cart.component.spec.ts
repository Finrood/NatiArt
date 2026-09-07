import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { CartComponent } from './cart.component';

describe('CartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CartComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('cancels the pending error dismissal on destroy (P2)', () => {
    const fixture = TestBed.createComponent(CartComponent);
    const component = fixture.componentInstance;
    const internals = component as unknown as {
      setError(message: string | null): void;
      errorDismissTimer: ReturnType<typeof setTimeout> | undefined;
    };
    const clearSpy: jasmine.Spy = spyOn(window, 'clearTimeout').and.callThrough();

    internals.setError('boom');
    expect(internals.errorDismissTimer).toBeDefined();

    fixture.destroy();
    expect(clearSpy).toHaveBeenCalled();
    expect(internals.errorDismissTimer).toBeUndefined();
  });
});
