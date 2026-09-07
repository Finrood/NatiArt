import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';

import { TopMenuComponent } from './top-menu.component';
import { CartService } from '../../../service/cart.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';

describe('TopMenuComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopMenuComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CartService, useValue: { getCartCount: (): Observable<number> => of(0) } },
        {
          provide: AuthenticationService,
          useValue: { isLoggedIn$: new BehaviorSubject<boolean>(false).asObservable() },
        },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('cancels the pending hover-close timer on destroy (P2)', () => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const clearSpy: jasmine.Spy = spyOn(window, 'clearTimeout').and.callThrough();

    component.showCartModal();
    component.hideCartModal();
    const internals = component as unknown as {
      cartHoverCloseTimer: ReturnType<typeof setTimeout> | undefined;
    };
    expect(internals.cartHoverCloseTimer).toBeDefined();

    fixture.destroy();
    expect(clearSpy).toHaveBeenCalled();
    expect(internals.cartHoverCloseTimer).toBeUndefined();
  });
});
