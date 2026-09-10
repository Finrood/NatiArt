import {TestBed} from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  Router,
  RouterStateSnapshot
} from '@angular/router';
import {firstValueFrom, Observable, of, throwError} from 'rxjs';

import {productGuard} from './product-guard.guard';
import {Product} from '../models/product.model';
import {ProductService} from '../service/product.service';

describe('productGuard', () => {
  let productService: jasmine.SpyObj<ProductService>;
  let navigate: jasmine.Spy;

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

  const resolve = async (result: Observable<boolean> | boolean): Promise<boolean> =>
    result instanceof Observable ? firstValueFrom(result) : result;

  beforeEach(() => {
    productService = jasmine.createSpyObj<ProductService>('ProductService', ['getProduct']);
    TestBed.configureTestingModule({
      providers: [{provide: ProductService, useValue: productService}]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  it('allowsDeactivationWhenTheProductFetchSucceeds', async () => {
    productService.getProduct.and.returnValue(of({id: 'p1'} as Product));

    await expectAsync(resolve(run('p1'))).toBeResolvedTo(true);

    expect(navigate).not.toHaveBeenCalled();
  });

  it('blocksAndRedirectsToTheDashboardWhenTheProductFetchFails', async () => {
    productService.getProduct.and.returnValue(throwError(() => new Error('backend down')));

    await expectAsync(resolve(run('p1'))).toBeResolvedTo(false);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('blocksWithoutNetworkEgressWhenTheRouteCarriesNoId', async () => {
    await expectAsync(resolve(run(null))).toBeResolvedTo(false);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(productService.getProduct).not.toHaveBeenCalled();
  });
});

