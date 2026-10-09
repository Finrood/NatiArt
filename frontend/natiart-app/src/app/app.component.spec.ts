import {Component, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {Event, NavigationEnd, NavigationError, provideRouter, Router, Scroll} from '@angular/router';
import {APP_BASE_HREF, ViewportScroller} from '@angular/common';

import {of, Subject} from 'rxjs';
import {AuthenticationService} from './directory/service/authentication.service';
import {AppComponent} from './app.component';

@Component({template: '<h1>First page</h1>'})
class FirstPage {}
const $ready = signal(false);
@Component({template: '@if ($loaded()) { <h1>Loaded page</h1> } @else { <p>Loading</p> }'})
class LoadingPage { readonly $loaded = $ready; }
@Component({template: '<main [attr.aria-busy]="!$loaded()"><h1>Catalog</h1>@if ($loaded()) { <p>Products</p> }</main>'})
class BusyPage { readonly $loaded = $ready; }
@Component({template: '<h1>Collections</h1><input id="catalog-search" aria-label="Search products">'})
class SearchPage {}

describe('AppComponent', () => {
  beforeEach(async () => {
    $ready.set(false);
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        {provide: APP_BASE_HREF, useValue: '/en/'},
        {provide: AuthenticationService, useValue: {isLoggedIn$: of(false), currentUser$: of(null), resetInactivityTimer: () => undefined}},
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{path: 'first', component: FirstPage}, {path: 'loaded', component: LoadingPage},
          {path: 'busy', component: BusyPage}, {path: 'search', component: SearchPage}]),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'NatiArt' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('NatiArt');
  });

  it('should render the router outlet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });

  it('skips the header without changing the current route or filters under a localized base URL', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router: Router = TestBed.inject(Router);
    const scroll: jasmine.Spy = spyOn(TestBed.inject(ViewportScroller), 'scrollToAnchor');
    fixture.detectChanges();
    await router.navigateByUrl('/search?query=plate&categoryId=tableware#old-anchor');
    fixture.detectChanges();
    await fixture.whenStable();
    const host: HTMLElement = fixture.nativeElement;
    const link: HTMLAnchorElement = host.querySelector('.skip-link')!;
    expect(link.getAttribute('href')).toBe('/en/search?query=plate&categoryId=tableware#store-content');
    const navigation: jasmine.Spy = spyOn(router, 'navigateByUrl').and.callThrough();
    const click: MouseEvent = new MouseEvent('click', {bubbles: true, cancelable: true});
    link.dispatchEvent(click);
    await fixture.whenStable();
    expect(click.defaultPrevented).toBeTrue();
    expect(navigation).not.toHaveBeenCalled();
    expect(router.url).toBe('/search?query=plate&categoryId=tableware#old-anchor');
    expect(document.activeElement).toBe(host.querySelector('#store-content'));
    expect(scroll).toHaveBeenCalledWith('store-content');
    fixture.destroy();
  });
  it('focuses each routed heading once, including a heading loaded asynchronously', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router: Router = TestBed.inject(Router);
    const focus: jasmine.Spy = spyOn(HTMLElement.prototype, 'focus').and.callThrough();
    fixture.detectChanges();
    await router.navigateByUrl('/first');
    fixture.detectChanges();
    await fixture.whenStable();
    const first: HTMLElement = fixture.nativeElement.querySelector('router-outlet').nextElementSibling.querySelector('h1');
    expect(focus.calls.mostRecent().object).toBe(first);
    expect(focus.calls.mostRecent().args).toEqual([{preventScroll: true}]);
    expect(fixture.nativeElement.querySelectorAll('app-top-menu').length).toBe(1);
    await router.navigateByUrl('/loaded');
    fixture.detectChanges();
    await fixture.whenStable();
    $ready.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const loaded: HTMLElement = fixture.nativeElement.querySelector('router-outlet').nextElementSibling.querySelector('h1');
    expect(focus.calls.mostRecent().object).toBe(loaded);
    const focusCount: number = focus.calls.count();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(focus.calls.count()).toBe(focusCount);
    fixture.destroy();
  });

  it('restores Back scroll after asynchronous content renders and resets a new route to the top', async () => {
    const router: Router = TestBed.inject(Router);
    const events = new Subject<Event>();
    spyOnProperty(router, 'events', 'get').and.returnValue(events);
    const scroller: ViewportScroller = TestBed.inject(ViewportScroller);
    const scroll: jasmine.Spy = spyOn(scroller, 'scrollToPosition');
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await router.navigateByUrl('/busy');
    events.next(new NavigationEnd(1, '/busy', '/busy'));
    events.next(new Scroll(new NavigationEnd(1, '/busy', '/busy'), [0, 1988], null));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(scroll).not.toHaveBeenCalled();
    $ready.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(scroll).toHaveBeenCalledOnceWith([0, 1988]);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(scroll.calls.count()).toBe(1);
    await router.navigateByUrl('/first');
    events.next(new NavigationEnd(2, '/first', '/first'));
    events.next(new Scroll(new NavigationEnd(2, '/first', '/first'), null, null));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(scroll.calls.mostRecent().args).toEqual([[0, 0]]);
    fixture.destroy();
  });

  it('offers visible recovery when a route cannot load and clears it after a successful navigation', async () => {
    const router: Router = TestBed.inject(Router);
    const events: Subject<Event> = new Subject<Event>();
    spyOnProperty(router, 'events', 'get').and.returnValue(events);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await router.navigateByUrl('/first');
    fixture.detectChanges();
    events.next(new NavigationError(2, '/loaded', new TypeError('Failed to fetch dynamically imported module')));
    fixture.detectChanges(); await fixture.whenStable();
    const host: HTMLElement = fixture.nativeElement;
    const alert: HTMLElement = host.querySelector('[data-navigation-error]')!;
    expect(alert.textContent).toContain('shop may have been updated');
    expect(alert.textContent).toContain('select any uploaded artwork again');
    expect(alert.querySelector('button')!.textContent).toContain('Reload page');
    expect(document.activeElement).toBe(alert);
    expect(host.querySelector('h1')!.textContent).toBe('First page');
    events.next(new NavigationEnd(3, '/first', '/first'));
    fixture.detectChanges(); await fixture.whenStable();
    expect(host.querySelector('[data-navigation-error]')).toBeNull();
    fixture.destroy();
  });

  it('honors search focus for a clear action once and keeps normal navigation heading focus', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    const router: Router = TestBed.inject(Router);
    const focus: jasmine.Spy = spyOn(HTMLElement.prototype, 'focus').and.callThrough();
    fixture.detectChanges();
    await router.navigateByUrl('/search?query=print');
    fixture.detectChanges();
    await fixture.whenStable();
    await router.navigateByUrl('/search?page=0', {info: 'catalog-search'});
    fixture.detectChanges();
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#catalog-search');
    expect(focus.calls.mostRecent().object).toBe(input);
    const focusCount: number = focus.calls.count();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(focus.calls.count()).toBe(focusCount);
    await router.navigateByUrl('/search?query=plate');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(focus.calls.mostRecent().object).toBe(fixture.nativeElement.querySelector('h1'));
    fixture.destroy();
  });

});
