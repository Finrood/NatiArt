import {Component, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {Event, NavigationEnd, provideRouter, Router, Scroll} from '@angular/router';
import {ViewportScroller} from '@angular/common';

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

describe('AppComponent', () => {
  beforeEach(async () => {
    $ready.set(false);
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        {provide: AuthenticationService, useValue: {isLoggedIn$: of(false), resetInactivityTimer: () => undefined}},
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{path: 'first', component: FirstPage}, {path: 'loaded', component: LoadingPage},
          {path: 'busy', component: BusyPage}]),
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

});
