import {BehaviorSubject, of} from 'rxjs';
import {CartService} from './cart.service';
import {CartModalComponent} from '../components/customer/cart-modal/cart-modal.component';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from './image-loader.service';
import {ProductService} from './product.service';
import {OrderSummaryComponent} from '../components/customer/checkout/order-summary/order-summary.component';
import {CartItem} from '../models/CartItem.model';
import {Product} from '../models/product.model';

function line(path: string | undefined): CartItem {
  const product: Product = {id: 'p1', label: 'Vase', originalPrice: 10, markedPrice: 10, stockQuantity: 5, categoryId: 'cat',
    availablePersonalizations: [], tags: [], images: path ? [path] : []};
  return {cartItemId: 'same-line', product, quantity: 1};
}
function image(): Blob { return new Blob(['<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1"/></svg>'], {type: 'image/svg+xml'}); }

describe('Shared image ownership at HTTP and rendered boundaries', (): void => {
  let http: HttpTestingController;
  let owners: ImageCollection[];
  let cartItems: BehaviorSubject<CartItem[]>;
  beforeEach((): void => {
    cartItems = new BehaviorSubject([line('shared')]);
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), {provide: CartService, useValue: {getCartItems: () => cartItems.asObservable(), getCartTotal: () => of(10)}}]});
    http = TestBed.inject(HttpTestingController); owners = [];
  });
  afterEach((): void => { owners.forEach((owner: ImageCollection): void => owner.destroy()); http.verify(); });
  function owner(): ImageCollection { const value: ImageCollection = TestBed.inject(ImageLoaderService).create(); owners.push(value); return value; }

  it('shares one delayed GET across owners and repeated updates, releasing each raw URL on removal', (): void => {
    const first: ImageCollection = owner(); const second: ImageCollection = owner();
    const create: jasmine.Spy = spyOn(URL, 'createObjectURL').and.returnValues('blob:first', 'blob:second');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    first.update([{key: 'one', source: 'shared'}]); second.update([{key: 'two', source: 'shared'}]);
    first.update([{key: 'one', source: 'shared'}]);
    http.expectOne((request): boolean => request.params.get('path') === 'shared').flush(image());
    expect(create.calls.count()).toBe(2); expect(first.states()['one']).toBe('loaded');
    first.update([]); expect(revoke).toHaveBeenCalledWith('blob:first');
    expect(second.urls()['two']).toBe('blob:second'); second.destroy(); expect(revoke).toHaveBeenCalledWith('blob:second');
  });

  it('cancels the last pending owner, and fresh browsing under the same path refetches bytes', (): void => {
    const view: ImageCollection = owner(); view.update([{key: 'same', source: 'art'}]);
    const old = http.expectOne((request): boolean => request.params.get('path') === 'art');
    view.update([]); expect(old.cancelled).toBeTrue();
    view.update([{key: 'same', source: 'art'}]); http.expectOne((request): boolean => request.params.get('path') === 'art').flush(image());
    view.update([]); view.update([{key: 'same', source: 'art'}]);
    http.expectOne((request): boolean => request.params.get('path') === 'art').flush(image());
  });

  it('replaces a path or local File under an unchanged line ID and immediately revokes the previous URL', (): void => {
    const view: ImageCollection = owner(); const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    view.update([{key: 'same', source: 'a'}]); http.expectOne((request): boolean => request.params.get('path') === 'a').flush(image());
    const before: string = view.urls()['same']; view.update([{key: 'same', source: 'b'}]); expect(revoke).toHaveBeenCalledWith(before);
    http.expectOne((request): boolean => request.params.get('path') === 'b').flush(image());
    const remote: string = view.urls()['same']; view.update([{key: 'same', source: new File([image()], 'local.svg')}]);
    expect(revoke).toHaveBeenCalledWith(remote); const local: string = view.urls()['same'];
    view.update([{key: 'same', source: new File([image()], 'replacement.svg')}]); expect(revoke).toHaveBeenCalledWith(local);
  });

  it('invalidates unchanged image paths after a successful product replacement', (): void => {
    const view: ImageCollection = owner(); const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    view.update([{key: 'same', source: 'art'}]); http.expectOne((request): boolean => request.params.get('path') === 'art').flush(image());
    const before: string = view.urls()['same']; TestBed.inject(ProductService).updateProduct('p1', new FormData()).subscribe();
    http.expectOne((request): boolean => request.method === 'PUT').flush(line('art').product);
    expect(revoke).toHaveBeenCalledWith(before); expect(view.states()['same']).toBe('loading');
    http.expectOne((request): boolean => request.params.get('path') === 'art').flush(image()); expect(view.urls()['same']).not.toBe(before);
  });

  it('uses a one-shot accessible fallback for absent, HTTP-failed and decoding-failed images', (): void => {
    const view: ImageCollection = owner(); const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    view.update([{key: 'none', source: undefined}, {key: 'bad', source: 'bad'}, {key: 'decode', source: 'decode'}]);
    http.expectOne((request): boolean => request.params.get('path') === 'bad').flush(null, {status: 404, statusText: 'Missing'});
    http.expectOne((request): boolean => request.params.get('path') === 'decode').flush(image()); const decoded: string = view.urls()['decode'];
    view.failed('decode'); view.failed('decode'); expect(revoke.calls.count()).toBe(1); expect(revoke).toHaveBeenCalledWith(decoded);
    expect(view.states()).toEqual({none: 'empty', bad: 'error', decode: 'error'});
    expect(Object.values(view.urls()).every((url: string): boolean => url === EMPTY_PRODUCT_IMAGE)).toBeTrue();
    view.update([{key: 'none', source: undefined}, {key: 'bad', source: 'bad'}, {key: 'decode', source: 'decode'}]); http.expectNone((request): boolean => request.url.includes('placeholder'));
  });

  it('bounds repeated browsing without retaining completed requests or owned URLs', (): void => {
    const view: ImageCollection = owner(); const create: jasmine.Spy = spyOn(URL, 'createObjectURL').and.returnValue('blob:owned');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    for (let index: number = 0; index < 100; index++) {
      view.update([{key: 'same', source: 'art-' + index}]); http.expectOne((request): boolean => request.params.get('path') === 'art-' + index).flush(image());
    }
    view.destroy(); expect(create.calls.count()).toBe(100); expect(revoke.calls.count()).toBe(100); expect(view.urls()).toEqual({});
    TestBed.inject(ProductService).getImage('art-0').subscribe(); http.expectOne((request): boolean => request.params.get('path') === 'art-0').flush(image());
  });

  it('deduplicates actual cart emissions with a concurrent summary and cancels a removed delayed line', async (): Promise<void> => {
    const items: BehaviorSubject<CartItem[]> = cartItems;
    const cart: ComponentFixture<CartModalComponent> = TestBed.createComponent(CartModalComponent);
    const summary: ComponentFixture<OrderSummaryComponent> = TestBed.createComponent(OrderSummaryComponent);
    summary.componentRef.setInput('cartItems', [line('shared')]); cart.autoDetectChanges(); summary.autoDetectChanges();
    items.next([{...line('shared'), quantity: 2}]); items.next([line('shared')]);
    http.expectOne((request): boolean => request.params.get('path') === 'shared').flush(image());
    await cart.whenStable(); await summary.whenStable();
    expect((cart.nativeElement as HTMLElement).querySelector('img')!.src.startsWith('blob:')).toBeTrue();
    expect((summary.nativeElement as HTMLElement).querySelector('img')!.src.startsWith('blob:')).toBeTrue();
    items.next([line('delayed')]); await cart.whenStable();
    const pending = http.expectOne((request): boolean => request.params.get('path') === 'delayed');
    items.next([]); await cart.whenStable(); expect(pending.cancelled).toBeTrue();
    expect((cart.nativeElement as HTMLElement).querySelector('img')).toBeNull(); cart.destroy(); summary.destroy();
  });

  it('renders delayed summary images through signals and refreshes an unchanged line without another user action', async (): Promise<void> => {
    const fixture: ComponentFixture<OrderSummaryComponent> = TestBed.createComponent(OrderSummaryComponent);
    fixture.componentRef.setInput('cartItems', [line('a')]); fixture.autoDetectChanges();
    http.expectOne((request): boolean => request.params.get('path') === 'a').flush(image()); await fixture.whenStable();
    const root: HTMLElement = fixture.nativeElement; const old: string = root.querySelector('img')!.src;
    expect(old.startsWith('blob:')).toBeTrue();
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL'); fixture.componentRef.setInput('cartItems', [line('b')]); fixture.detectChanges();
    expect(revoke).toHaveBeenCalledWith(old); http.expectOne((request): boolean => request.params.get('path') === 'b').flush(image()); await fixture.whenStable();
    expect(root.querySelector('img')!.src).not.toBe(old);
    fixture.componentRef.setInput('cartItems', [line(undefined)]); fixture.detectChanges(); await fixture.whenStable();
    expect(root.querySelector('img')!.src.startsWith('data:image/svg+xml')).toBeTrue(); expect(root.querySelector('img')!.alt).toBe('Vase');
    fixture.componentRef.setInput('cartItems', []); fixture.detectChanges(); expect(fixture.componentInstance.images.urls()).toEqual({}); fixture.destroy();
  });
});
