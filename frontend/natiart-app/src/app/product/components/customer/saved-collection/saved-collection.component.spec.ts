import {ComponentFixture, fakeAsync, TestBed, tick} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, ParamMap, provideRouter} from '@angular/router';
import {HttpRequest, provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {BehaviorSubject} from 'rxjs';
import {SavedCollectionComponent} from './saved-collection.component';
import {SavedCollectionService} from '../../../service/saved-collection.service';
import {Product} from '../../../models/product.model';
import {environment} from '../../../../../environments/environment';

const piece = (id: string): Product => ({id, label: 'Piece ' + id, categoryId: 'c', originalPrice: 100,
  markedPrice: 90, stockQuantity: 4, availablePersonalizations: [], images: [], tags: []});

describe('Saved collection live details and sharing', (): void => {
  let fixture: ComponentFixture<SavedCollectionComponent>;
  let http: HttpTestingController;
  let collection: SavedCollectionService;
  let params: BehaviorSubject<ParamMap>;
  const api: string = environment.api.product.url;
  beforeEach(async (): Promise<void> => {
    localStorage.removeItem('natiart-saved-collection-v1');
    sessionStorage.removeItem('natiart-comparison-v1');
    params = new BehaviorSubject<ParamMap>(convertToParamMap({}));
    await TestBed.configureTestingModule({imports: [SavedCollectionComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: ActivatedRoute, useValue: {queryParamMap: params}}
    ]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    collection = TestBed.inject(SavedCollectionService);
  });
  afterEach((): void => {
    fixture?.destroy(); http.verify(); localStorage.removeItem('natiart-saved-collection-v1');
    sessionStorage.removeItem('natiart-comparison-v1');
  });
  function open(ids: string[] = ['a', 'b']): void {
    collection.saveMany(ids.map(piece));
    fixture = TestBed.createComponent(SavedCollectionComponent);
    fixture.detectChanges();
  }
  function request(id: string): TestRequest { return http.expectOne(`${api}/products/${id}`); }

  it('checks current prices and sold-out states and cancels owned image work on leaving', (): void => {
    open();
    const fresh: TestRequest = request('a');
    expect(fresh.request.cache).toBe('no-store');
    fresh.flush({...piece('a'), markedPrice: 23.5, stockQuantity: 0, images: ['a.webp']});
    const image: TestRequest = http.expectOne((r: HttpRequest<unknown>): boolean => r.url === `${api}/images`);
    const pending: TestRequest = request('b');
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('23.50');
    expect(root.textContent).toContain('Out of Stock');
    fixture.destroy();
    expect(image.cancelled && pending.cancelled).toBeTrue();
  });

  it('keeps a checked neighbor while retrying a network error, without exposing stale prices', (): void => {
    open();
    request('a').flush({...piece('a'), markedPrice: 25});
    request('b').flush('offline', {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('We could not check this piece');
    expect(fixture.nativeElement.querySelectorAll('.product-price').length).toBe(1);
    fixture.componentInstance.retry('b');
    http.expectNone(`${api}/products/a`);
    request('b').flush({...piece('b'), markedPrice: 12});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.product-price').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('12.00');
  });

  it('bounds a stalled detail request and makes the saved choice retryable', fakeAsync((): void => {
    open(['a']);
    const pending: TestRequest = request('a');
    tick(10001); fixture.detectChanges();
    expect(pending.cancelled).toBeTrue();
    expect(fixture.componentInstance.$busy()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Retry this piece');
    expect(collection.isSaved('a')).toBeTrue();
  }));

  it('limits simultaneous product checks to four and cancels the queued tail', (): void => {
    open(['a', 'b', 'c', 'd', 'e', 'f']);
    const first: TestRequest = request('a');
    const pending: TestRequest[] = ['b', 'c', 'd'].map(request);
    http.expectNone(`${api}/products/e`);
    first.flush(piece('a'));
    const fifth: TestRequest = request('e');
    fixture.destroy();
    expect([...pending, fifth].every((r: TestRequest): boolean => r.cancelled)).toBeTrue();
    http.expectNone(`${api}/products/f`);
  });

  it('does not import shared pieces automatically and keeps only successfully checked pieces on explicit action', (): void => {
    params.next(convertToParamMap({pieces: 'a,b,missing'}));
    open(['mine']);
    request('a').flush(piece('a'));
    request('b').flush({...piece('b'), stockQuantity: 0});
    request('missing').flush('gone', {status: 404, statusText: 'Not found'});
    fixture.detectChanges();
    expect(collection.$pieces().map(saved => saved.id)).toEqual(['mine']);
    expect(fixture.nativeElement.textContent).toContain('shared with you');
    expect(fixture.nativeElement.querySelector('.collection-remove')).toBeNull();
    fixture.componentInstance.keep(); fixture.detectChanges();
    expect(collection.$pieces().map(saved => saved.id)).toEqual(['mine', 'a', 'b']);
    expect(fixture.componentInstance.$rows().length).toBe(3);
    http.expectNone(`${api}/products/mine`);
  });

  it('rejects invalid or duplicated shared parameters before any product request', (): void => {
    params.next(convertToParamMap({pieces: ['a', 'b']}));
    open(['mine']);
    expect(fixture.nativeElement.textContent).toContain('incomplete or invalid');
    http.expectNone(`${api}/products/a`);
    http.expectNone(`${api}/products/mine`);
    params.next(convertToParamMap({pieces: '../private'})); fixture.detectChanges();
    expect(fixture.componentInstance.$rows()).toEqual([]);
  });

  it('shows unavailable or unexpected products without a purchase link, and permits removal with undo', async (): Promise<void> => {
    open();
    request('a').flush({...piece('a'), active: false});
    request('b').flush(piece('unexpected'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.product-overview')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('no longer available to view');
    fixture.componentInstance.remove('a'); fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('h1'));
    expect(collection.$undo().length).toBe(1);
    fixture.componentInstance.undo(); fixture.detectChanges();
    request('a').flush(piece('a'));
    expect(collection.isSaved('a')).toBeTrue();
  });

  it('constructs a same-origin public selection link without forwarding other route data', (): void => {
    params.next(convertToParamMap({pieces: 'a,b', token: 'never-forward', email: 'never-forward'}));
    open([]);
    request('a').flush(piece('a')); request('b').flush(piece('b'));
    const url: URL = new URL(fixture.componentInstance.$shareUrl());
    expect(url.origin).toBe(location.origin);
    expect(url.searchParams.get('pieces')).toBe('a,b');
    expect([...url.searchParams.keys()]).toEqual(['pieces']);
    expect(url.hash).toBe('');
  });

  it('revokes loaded image URLs and rechecks every piece on explicit refresh', (): void => {
    open(['a']);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:collection-test');
    const revoked: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    request('a').flush({...piece('a'), images: ['a.webp']});
    http.expectOne((r: HttpRequest<unknown>): boolean => r.url === `${api}/images`).flush(new Blob(['art']));
    fixture.componentInstance.refresh();
    expect(revoked).toHaveBeenCalledWith('blob:collection-test');
    request('a').flush({...piece('a'), markedPrice: 10}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('10.00');
  });

  it('offers a readable link and useful fallback when clipboard access fails', async (): Promise<void> => {
    open(['a']); request('a').flush(piece('a')); fixture.detectChanges();
    fixture.componentInstance.share(); fixture.detectChanges(); await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#collection-link');
    expect(input.readOnly).toBeTrue();
    expect(input.value).toContain('pieces=a');
    if (navigator.clipboard) spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.reject(new Error('Blocked')));
    await fixture.componentInstance.copy(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Copy the link from the field');
  });
});
