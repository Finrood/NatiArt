import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {ProductDetailComponent} from './product-detail.component';
import {TopMenuComponent} from '../top-menu/top-menu.component';
import {LeftMenuComponent} from '../left-menu/left-menu.component';
import {Product} from '../../../models/product.model';
import {ProductService} from '../../../service/product.service';

@Component({selector: 'app-top-menu', template: ''}) class HeaderStub {}
@Component({selector: 'app-left-menu', template: ''}) class SidebarStub {}
function payload(id: string, images: string[] = []): Product {
  return JSON.parse(JSON.stringify({id, label: 'Painted vase ' + id, originalPrice: 10, markedPrice: 10, stockQuantity: 2,
    categoryId: 'category-uuid', categoryLabel: 'Porcelain artwork', packageId: 'package-uuid', packageLabel: 'Gift box',
    tags: ['Hand painted', 'Porcelain'], images, availablePersonalizations: []})) as Product;
}
function bytes(): Blob { return new Blob(['<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10"/></svg>'], {type: 'image/svg+xml'}); }

describe('Product detail rendered HTTP recovery contract', (): void => {
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let http: HttpTestingController;
  let fixture: ComponentFixture<ProductDetailComponent>;
  beforeEach((): void => {
    params = new BehaviorSubject(convertToParamMap({id: 'first'}));
    TestBed.configureTestingModule({imports: [ProductDetailComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: ActivatedRoute, useValue: {paramMap: params.asObservable()}}]});
    TestBed.overrideComponent(ProductDetailComponent, {remove: {imports: [TopMenuComponent, LeftMenuComponent]}, add: {imports: [HeaderStub, SidebarStub]}});
    http = TestBed.inject(HttpTestingController); fixture = TestBed.createComponent(ProductDetailComponent);
    document.body.appendChild(fixture.nativeElement); fixture.autoDetectChanges();
  });
  afterEach((): void => { fixture.destroy(); http.verify(); });
  function reply(id: string, images: string[] = []): void {
    http.expectOne((request): boolean => request.url.endsWith('/products/' + id)).flush(payload(id, images));
    http.expectOne((request): boolean => request.url.endsWith('/products')).flush([]);
  }

  it('positions an existing lens on the first zoom click and safely handles later removal', async (): Promise<void> => {
    reply('first', ['art']); http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'art').flush(bytes()); await fixture.whenStable();
    const container: HTMLElement = (fixture.nativeElement as HTMLElement).querySelector('.cursor-zoom-in')!;
    const rect: DOMRect = container.getBoundingClientRect();
    expect((): void => { container.dispatchEvent(new MouseEvent('click', {bubbles: true, clientX: rect.left + 10, clientY: rect.top + 10})); }).not.toThrow();
    await fixture.whenStable(); const lens: HTMLElement = container.querySelector('div[hidden],div.pointer-events-none')!;
    expect(fixture.componentInstance.isZoomed).toBeTrue(); expect(lens.hidden).toBeFalse(); expect(lens.style.left.endsWith('px')).toBeTrue();
    expect(lens.style.left).not.toContain('NaN');
    expect(container.querySelector('img')!.style.transformOrigin).toContain('%');
    params.next(convertToParamMap({id: 'empty'})); reply('empty'); await fixture.whenStable();
    expect(fixture.componentInstance.isZoomed).toBeFalse(); expect((): void => fixture.componentInstance.updateZoomPosition(new MouseEvent('mousemove'))).not.toThrow();
  });

  it('recovers a failed then missing route to a valid product and renders JSON tags/reference labels', async (): Promise<void> => {
    http.expectOne((request): boolean => request.url.endsWith('/products/first')).flush(null, {status: 404, statusText: 'Missing'}); await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Could not load');
    params.next(convertToParamMap({})); await fixture.whenStable(); http.expectNone((request): boolean => request.url.includes('/products/null'));
    params.next(convertToParamMap({id: 'valid'})); reply('valid'); await fixture.whenStable();
    const text: string = (fixture.nativeElement as HTMLElement).textContent!;
    expect(text).toContain('Hand painted'); expect(text).toContain('Porcelain artwork'); expect(text).toContain('Gift box');
    expect(text).not.toContain('category-uuid'); expect(text).not.toContain('package-uuid');
    expect((fixture.nativeElement as HTMLElement).querySelector('img')!.src.startsWith('data:image/svg+xml')).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).querySelector('.animate-pulse')).toBeNull();
  });

  it('keeps server image order and selects the clicked identity after out-of-order responses', async (): Promise<void> => {
    reply('first', ['image-z', 'image-a', 'image-m']);
    const requests = http.match((request): boolean => new URL(request.urlWithParams, document.baseURI).pathname.endsWith('/images'));
    requests.find((request): boolean => new URL(request.request.urlWithParams, document.baseURI).searchParams.get('path') === 'image-a')!.flush(bytes());
    requests.find((request): boolean => new URL(request.request.urlWithParams, document.baseURI).searchParams.get('path') === 'image-m')!.flush(bytes());
    requests.find((request): boolean => new URL(request.request.urlWithParams, document.baseURI).searchParams.get('path') === 'image-z')!.flush(bytes()); await fixture.whenStable();
    expect(fixture.componentInstance.$productImages().map((entry): string => entry.key)).toEqual(['image-z', 'image-a', 'image-m']);
    const root: HTMLElement = fixture.nativeElement; const thumbnails: NodeListOf<HTMLImageElement> = root.querySelectorAll('img.cursor-pointer');
    thumbnails[1].click(); await fixture.whenStable();
    expect(root.querySelector('img')!.src).toBe(fixture.componentInstance.$productImages()[1].url!);
    expect(fixture.componentInstance.selectedImageIndex).toBe(1);
  });

  it('falls back once for failed image HTTP or decoding, revoking raw bytes without another GET', async (): Promise<void> => {
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    reply('first', ['missing', 'decode']);
    http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'missing').flush(null, {status: 404, statusText: 'Missing'});
    http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'decode').flush(bytes()); await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement; const thumbs: NodeListOf<HTMLImageElement> = root.querySelectorAll('img.cursor-pointer');
    expect(thumbs[0].src.startsWith('data:image/svg+xml')).toBeTrue();
    const decoded: string = thumbs[1].src; thumbs[1].dispatchEvent(new Event('error')); await fixture.whenStable();
    expect(revoke).toHaveBeenCalledWith(decoded); expect(thumbs[1].src.startsWith('data:image/svg+xml')).toBeTrue();
    thumbs[1].dispatchEvent(new Event('error')); await fixture.whenStable(); expect(revoke.calls.count()).toBe(1);
    expect(fixture.componentInstance.$productImages().every((entry): boolean => entry.state === 'error')).toBeTrue();
  });

  it('refreshes the visible detail image on a successful product edit and releases its old URL', async (): Promise<void> => {
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    reply('first', ['art']);
    http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'art').flush(bytes());
    await fixture.whenStable();
    const oldUrl: string = fixture.componentInstance.$productImages()[0].url!;
    TestBed.inject(ProductService).updateProduct('first', new FormData()).subscribe();
    http.expectOne((request): boolean => request.method === 'PUT' && request.url.endsWith('/products/first')).flush(payload('first', ['art']));
    expect(revoke).toHaveBeenCalledWith(oldUrl);
    http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'art').flush(bytes());
    await fixture.whenStable();
    expect(fixture.componentInstance.$productImages()[0].state).toBe('loaded');
    expect(fixture.componentInstance.$productImages()[0].url).not.toBe(oldUrl);
  });

  it('cancels a personalization refresh when the route leaves its product', async (): Promise<void> => {
    reply('first'); await fixture.whenStable();
    fixture.componentInstance.openPersonalizationModal(payload('first'));
    fixture.componentInstance.onPersonalizationComplete({});
    const pending = http.expectOne((request): boolean => request.url.endsWith('/products/first'));
    params.next(convertToParamMap({id: 'next'})); expect(pending.cancelled).toBeTrue(); reply('next'); await fixture.whenStable();
    expect(fixture.componentInstance.showPersonalizationModal).toBeFalse(); http.expectNone((request): boolean => request.method === 'POST');
  });

  it('cancels pending images and related requests immediately on rapid route change', async (): Promise<void> => {
    http.expectOne((request): boolean => request.url.endsWith('/products/first')).flush(payload('first', ['old']));
    const oldRelated = http.expectOne((request): boolean => request.url.endsWith('/products'));
    const oldImage = http.expectOne((request): boolean => new URL(request.urlWithParams, document.baseURI).searchParams.get('path') === 'old');
    params.next(convertToParamMap({id: 'next'})); expect(oldRelated.cancelled).toBeTrue(); expect(oldImage.cancelled).toBeTrue();
    const next = http.expectOne((request): boolean => request.url.endsWith('/products/next'));
    params.next(convertToParamMap({id: 'last'})); expect(next.cancelled).toBeTrue(); reply('last'); await fixture.whenStable();
    expect(fixture.componentInstance.product$.value?.id).toBe('last'); expect(fixture.componentInstance.$productImages()).toEqual([]);
  });
});
