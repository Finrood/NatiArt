import {CanDeactivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {catchError, map, of} from 'rxjs';
import {ProductService} from "../service/product.service";
import {ProductDetailComponent} from "../components/customer/product-detail/product-detail.component";

export const productGuard: CanDeactivateFn<ProductDetailComponent> = (
  component: ProductDetailComponent,
  currentRoute,
  currentState,
  nextState
) => {
  const productService = inject(ProductService);
  const router = inject(Router);

  const id = currentRoute.paramMap.get('id');
  if (!id) {
    router.navigate(['/dashboard'])
      .then(() => {
      });
    return false;
  }

  return productService.getProduct(id).pipe(
    map(() => true),
    catchError(() => {
      router.navigate(['/dashboard'])
        .then(() => {
        });
      return of(false);
    })
  );
};
