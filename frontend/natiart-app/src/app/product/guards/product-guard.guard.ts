import {CanDeactivateFn} from '@angular/router';
import {ProductDetailComponent} from "../components/customer/product-detail/product-detail.component";

export const productGuard: CanDeactivateFn<ProductDetailComponent> = (
  _component: ProductDetailComponent,
  _currentRoute,
  _currentState,
  _nextState
) => true;
