import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router, Routes} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {of} from 'rxjs';
import {routes} from '../app.routes';
import {AuthenticationService} from '../directory/service/authentication.service';
import {RedirectService} from '../directory/service/redirect.service';
import {environment} from '../../environments/environment';
import {DashboardComponent} from './components/customer/dashboard/dashboard.component';
import {ProductDetailComponent} from './components/customer/product-detail/product-detail.component';
import {CheckoutComponent} from './components/customer/checkout/checkout.component';
import {CartComponent} from './components/customer/cart/cart.component';
import {CartService} from './service/cart.service';
import {Product} from './models/product.model';

@Component({template: '<h1>Sign in</h1>'})
class SignInDestination {}

describe('Guest shopping routes', () => {
  it('allows discovery, a local basket and checkout without sign-in', async () => {
    localStorage.removeItem('natiart-cart');
    const testRoutes: Routes = routes.map(route => route.path === 'login'
      ? {path: 'login', component: SignInDestination} : route);
    await TestBed.configureTestingModule({providers: [provideRouter(testRoutes),
      provideHttpClient(), provideHttpClientTesting(),
      {provide: AuthenticationService, useValue: {
        isLoggedIn$: of(false), authResolved$: of(true), currentUser$: of(null),
      }},
    ]}).compileComponents();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const api: string = environment.api.product.url;
    const harness: RouterTestingHarness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dashboard', DashboardComponent);
    http.expectNone(api + '/categories/page');
    http.expectOne(api + '/products/featured').flush([]);
    http.expectOne(api + '/products/new').flush([]);

    await harness.navigateByUrl('/product/guest-product', ProductDetailComponent);
    const product: Product = {id: 'guest-product', label: 'Guest porcelain', categoryId: 'cat',
      markedPrice: 20, originalPrice: 25, stockQuantity: 3, images: [], tags: [], availablePersonalizations: []};
    http.expectNone(api + '/categories/page');
    http.expectOne(api + '/products/guest-product').flush(product);
    http.expectOne(request => request.url === api + '/products' && request.params.get('categoryId') === 'cat').flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Guest porcelain');
    TestBed.inject(CartService).addToCart(product, 1);
    await harness.navigateByUrl('/cart', CartComponent);
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Guest porcelain');

    await harness.navigateByUrl('/checkout', CheckoutComponent);
    http.expectOne(environment.api.directory.url + '/guest/session').flush({id: 'guest-session', csrfToken: 'proof', profile: null, remembered: false});
    expect(TestBed.inject(Router).url).toBe('/checkout');
    expect(harness.routeNativeElement!.textContent).toContain('Continue as a guest');
    expect(JSON.parse(localStorage.getItem('natiart-cart') || '{}').items.length).toBe(1);
    harness.fixture.destroy();
    http.verify();
    localStorage.removeItem('natiart-cart');
  });
});
