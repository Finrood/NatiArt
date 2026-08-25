import {TestBed} from '@angular/core/testing';
import {CanDeactivateFn} from '@angular/router';

import {productGuard} from './product-guard.guard';

describe('productGuard', () => {
  const executeGuard: CanDeactivateFn<unknown> = (...guardParameters) =>
    TestBed.runInInjectionContext(() =>
      (productGuard as unknown as (...args: unknown[]) => ReturnType<CanDeactivateFn<unknown>>)(...guardParameters)
    );

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
