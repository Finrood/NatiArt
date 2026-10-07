import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router, NavigationEnd} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {Location} from '@angular/common';
import {provideLocationMocks} from '@angular/common/testing';
import {of, firstValueFrom, filter, take} from 'rxjs';
import {CatalogComponent} from './catalog.component';
import {AuthenticationService} from '../../../../directory/service/authentication.service';
import {environment} from '../../../../../environments/environment';

const product = (id: string, categoryId: string) => ({id, categoryId, label: 'Print ' + id, originalPrice: 10,
  markedPrice: 10, stockQuantity: 5, images: [], availablePersonalizations: [], tags: []});

describe('Catalog URL-backed pagination', () => {
  let http: HttpTestingController;
  const api: string = environment.api.product.url;
  beforeEach(async () => {
    localStorage.removeItem('natiart-cart');
    await TestBed.configureTestingModule({imports: [CatalogComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([{path: 'products', component: CatalogComponent}]),
      provideLocationMocks(), {provide: AuthenticationService, useValue: {isLoggedIn$: of(true)}},
    ]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(Router).setUpLocationChangeListener();
  });
  function flushCategories(): void {
    http.expectOne((r) => r.url === `${api}/categories/page`).flush({items: [
      {id: 'A', label: 'Category A'}, {id: 'B', label: 'Category B'}], page: 0, size: 20, total: 2, hasNext: false});
  }

  it('loads the requested filtered page from the URL and restores it on Back after a rendered category click', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/products?categoryId=A&page=1&query=print', CatalogComponent);
    flushCategories();
    const first = http.expectOne((r) => r.url === `${api}/products/page`);
    expect(first.request.params.get('categoryId')).toBe('A');
    expect(first.request.params.get('page')).toBe('1');
    expect(first.request.params.get('query')).toBe('print');
    first.flush({items: [product('21', 'A')], page: 1, size: 20, total: 21, hasNext: false});
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Print 21');
    const filters: HTMLButtonElement = harness.routeNativeElement!.querySelector('button[aria-controls="category-filters"]')!;
    expect(filters.getAttribute('aria-expanded')).toBe('false');
    filters.click();
    harness.detectChanges();
    expect(filters.getAttribute('aria-expanded')).toBe('true');
    const category: HTMLAnchorElement = Array.from(harness.routeNativeElement!.querySelectorAll('a'))
      .find((a: HTMLAnchorElement) => a.textContent?.includes('Category B'))!;
    category.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(filters.getAttribute('aria-expanded')).toBe('false');
    expect(harness.routeNativeElement!.querySelector('a[aria-label="Clear category filter"]')!.textContent).toContain('Category B');
    const second = http.expectOne((r) => r.url === `${api}/products/page`);
    expect(second.request.params.get('categoryId')).toBe('B');
    expect(second.request.params.get('page')).toBe('0');
    second.flush({items: [product('B', 'B')], page: 0, size: 20, total: 1, hasNext: false});
    const navigated = firstValueFrom(TestBed.inject(Router).events.pipe(filter((event) => event instanceof NavigationEnd), take(1)));
    TestBed.inject(Location).back();
    await navigated;
    await harness.fixture.whenStable();
    const back = http.expectOne((r) => r.url === `${api}/products/page`);
    expect(back.request.params.get('categoryId')).toBe('A');
    expect(back.request.params.get('page')).toBe('1');
    expect(back.request.params.get('query')).toBe('print');
    back.flush({items: [product('21', 'A')], page: 1, size: 20, total: 21, hasNext: false});
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Print 21');
    const forwarded = firstValueFrom(TestBed.inject(Router).events.pipe(filter((event) => event instanceof NavigationEnd), take(1)));
    TestBed.inject(Location).forward();
    await forwarded;
    const forward = http.expectOne((r) => r.url === `${api}/products/page`);
    expect(forward.request.params.get('categoryId')).toBe('B');
    expect(forward.request.params.get('page')).toBe('0');
    forward.flush({items: [product('B', 'B')], page: 0, size: 20, total: 1, hasNext: false});
    harness.fixture.destroy();
    http.verify();
  });

  it('cancels a stale filtered page and retries a failed page without losing its URL state', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/products?categoryId=A&page=1', CatalogComponent);
    flushCategories();
    const stale = http.expectOne((r) => r.url === `${api}/products/page`);
    await harness.navigateByUrl('/products?categoryId=B&page=0', CatalogComponent);
    expect(stale.cancelled).toBeTrue();
    http.expectOne((r) => r.url === `${api}/products/page`).flush('down', {status: 500, statusText: 'Server Error'});
    harness.detectChanges();
    const retry: HTMLButtonElement = Array.from(harness.routeNativeElement!.querySelectorAll<HTMLButtonElement>('main button'))
      .find((button: HTMLButtonElement) => button.textContent?.trim() === 'Retry page')!;
    retry.click();
    const request = http.expectOne((r) => r.url === `${api}/products/page`);
    expect(request.request.params.get('categoryId')).toBe('B');
    expect(request.request.params.get('page')).toBe('0');
    request.flush({items: [product('B', 'B')], page: 0, size: 20, total: 1, hasNext: false});
    expect(TestBed.inject(Router).url).toContain('categoryId=B');
    harness.fixture.destroy();
    http.verify();
  });
});
