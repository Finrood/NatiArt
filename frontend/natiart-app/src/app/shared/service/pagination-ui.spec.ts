import {Type} from '@angular/core';
import {TestBed, ComponentFixture} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {CategoryManagementComponent} from '../../product/components/admin/admin-category-management/admin-category-management.component';
import {PackageManagementComponent} from '../../product/components/admin/admin-package-management/admin-package-management.component';
import {ProductManagementComponent} from '../../product/components/admin/admin-product-management/admin-product-management.component';
import {environment} from '../../../environments/environment';

function click(fixture: ComponentFixture<unknown>, text: string): void {
  const button: HTMLButtonElement | undefined = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
    .find((element: HTMLButtonElement) => element.textContent?.trim() === text);
  expect(button).withContext(text).toBeDefined();
  button!.click();
  fixture.detectChanges();
}

for (const resource of [
  {component: CategoryManagementComponent, path: 'categories', extra: {}},
  {component: PackageManagementComponent, path: 'packages', extra: {height: 1, width: 1, depth: 1}},
]) {
  describe(resource.path + ' rendered pagination', () => {
    let http: HttpTestingController;
    const url: string = `${environment.api.product.url}/admin/${resource.path}/page`;
    beforeEach(async () => {
      await TestBed.configureTestingModule({imports: [resource.component],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();
      http = TestBed.inject(HttpTestingController);
    });
    it('loads page two and opens the twenty-first row for editing', () => {
      const fixture: ComponentFixture<unknown> = TestBed.createComponent(resource.component as Type<unknown>);
      fixture.detectChanges();
      const items = Array.from({length: 20}, (_, i: number) => ({id: `row-${i}`, label: `Row ${i}`, active: true, ...resource.extra}));
      http.expectOne((request) => request.url === url).flush({items, page: 0, size: 20, total: 21, hasNext: true});
      fixture.detectChanges();
      click(fixture, 'Next page');
      const next = http.expectOne((request) => request.url === url);
      expect(next.request.params.get('page')).toBe('1');
      next.flush({items: [{id: 'row-20', label: 'Final row', active: true, ...resource.extra}], page: 1, size: 20, total: 21, hasNext: false});
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Final row');
      click(fixture, 'Edit');
      expect((fixture.nativeElement.querySelector('input[formControlName="label"]') as HTMLInputElement).value).toBe('Final row');
      fixture.destroy();
      http.verify();
    });
  });
}

describe('Product management paged reference selectors', () => {
  let http: HttpTestingController;
  const api: string = environment.api.product.url;
  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [ProductManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('opens the twenty-first product from a rendered later page', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const product = {id: 'last', label: 'Final art', categoryId: 'c', packageId: 'p', originalPrice: 10,
      markedPrice: 10, stockQuantity: 1, images: [], tags: [], availablePersonalizations: []};
    http.expectOne((r) => r.url === `${api}/admin/products/page`).flush({items: Array.from({length: 20}, (_, i: number) =>
      ({...product, id: `row-${i}`, label: `Art ${i}`})), page: 0, size: 20, total: 21, hasNext: true});
    http.expectOne((r) => r.url === `${api}/admin/categories/page`).flush({items: [{id: 'c', label: 'Category'}], page: 0, size: 20, total: 1, hasNext: false});
    http.expectOne((r) => r.url === `${api}/admin/packages/page`).flush({items: [{id: 'p', label: 'Package'}], page: 0, size: 20, total: 1, hasNext: false});
    fixture.detectChanges();
    click(fixture, 'Next page');
    const request = http.expectOne((r) => r.url === `${api}/admin/products/page`);
    expect(request.request.params.get('page')).toBe('1');
    request.flush({items: [product], page: 1, size: 20, total: 21, hasNext: false});
    fixture.detectChanges();
    click(fixture, 'Edit');
    expect((fixture.nativeElement.querySelector('input[formControlName="label"]') as HTMLInputElement).value).toBe('Final art');
    fixture.destroy();
    http.verify();
  });

  it('selects category and package beyond page one through rendered load-more controls', async () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${api}/admin/products/page`).flush({items: [], page: 0, size: 20, total: 0, hasNext: false});
    http.expectOne((r) => r.url === `${api}/admin/categories/page`).flush({items: [{id: 'c0', label: 'First category'}], page: 0, size: 20, total: 21, hasNext: true});
    http.expectOne((r) => r.url === `${api}/admin/packages/page`).flush({items: [{id: 'p0', label: 'First package'}], page: 0, size: 20, total: 21, hasNext: true});
    fixture.detectChanges();
    click(fixture, 'Add New Product');
    click(fixture, 'Load more categories');
    http.expectOne((r) => r.url === `${api}/admin/categories/page` && r.params.get('page') === '1')
      .flush({items: [{id: 'c20', label: 'Final category'}], page: 1, size: 20, total: 21, hasNext: false});
    click(fixture, 'Load more packages');
    http.expectOne((r) => r.url === `${api}/admin/packages/page` && r.params.get('page') === '1')
      .flush({items: [{id: 'p20', label: 'Final package'}], page: 1, size: 20, total: 21, hasNext: false});
    fixture.detectChanges();
    for (const [control, value] of [['categoryId', 'c20'], ['packageId', 'p20']]) {
      const select: HTMLSelectElement = fixture.nativeElement.querySelector(`select[formControlName="${control}"]`);
      select.value = value;
      select.dispatchEvent(new Event('change'));
    }
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[formControlName="label"]');
    input.value = 'Art';
    input.dispatchEvent(new Event('input'));
    const weight: HTMLInputElement = fixture.nativeElement.querySelector('input[formControlName="weightKg"]');
    weight.value = '0.5';
    weight.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    const request = http.expectOne((r) => r.method === 'POST');
    const dto = JSON.parse(await (request.request.body.get('productDto') as Blob).text());
    expect(dto.categoryId).toBe('c20');
    expect(dto.packageId).toBe('p20');
    request.flush({id: 'created', label: 'Art', images: []});
    http.expectOne((r) => r.url === `${api}/admin/products/page`).flush({items: [], page: 0, size: 20, total: 1, hasNext: false});
    fixture.destroy();
    http.verify();
  });
});
