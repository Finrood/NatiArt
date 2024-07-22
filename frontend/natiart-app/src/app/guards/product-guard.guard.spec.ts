import { TestBed } from '@angular/core/testing';
import { CanDeactivateFn } from '@angular/router';

import { productGuardGuard } from './product-guard.guard';

describe('productGuardGuard', () => {
  const executeGuard: CanDeactivateFn = (...guardParameters) => 
      TestBed.runInInjectionContext(() => productGuardGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
