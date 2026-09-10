import {TestBed} from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  RouterStateSnapshot
} from '@angular/router';
import {Observable} from 'rxjs';

import {productGuard} from './product-guard.guard';
import {Product} from '../models/product.model';
import {ProductService} from '../service/product.service';

describe('productGuard', () => {
  let productService: jasmine.SpyObj<ProductService>;

  const routeWithId = (id: string | null): ActivatedRouteSnapshot =>
    ({paramMap: convertToParamMap(id === null ? {} : {id})} as unknown as ActivatedRouteSnapshot);

  const run = (id: string | null): Observable<boolean> | boolean =>
    TestBed.runInInjectionContext(() =>
      (productGuard as unknown as (
        component: object,
        route: ActivatedRouteSnapshot,
        currentState: RouterStateSnapshot,
        nextState: RouterStateSnapshot
      ) => Observable<boolean> | boolean)(
        {}, routeWithId(id), {} as RouterStateSnapshot, {} as RouterStateSnapshot
      ));

  beforeEach(() => {
    productService = jasmine.createSpyObj<ProductService>('ProductService', ['getProduct']);
    TestBed.configureTestingModule({
      providers: [{provide: ProductService, useValue: productService}]
    });
  });

  it('allowsDeactivationWithoutFetchingTheProduct', () => {
    expect(run('p1')).toBeTrue();
    expect(productService.getProduct).not.toHaveBeenCalled();
  });

  it('allowsDeactivationWhenTheBackendWouldBeUnavailable', () => {
    productService.getProduct.and.throwError('backend down');

    expect(run('p1')).toBeTrue();
    expect(productService.getProduct).not.toHaveBeenCalled();
  });

  it('allowsDeactivationWhenTheRouteCarriesNoId', () => {
    expect(run(null)).toBeTrue();
    expect(productService.getProduct).not.toHaveBeenCalled();
  });
});
