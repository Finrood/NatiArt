import {Component, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';

import {of} from 'rxjs';
import {AuthenticationService} from './directory/service/authentication.service';
import {AppComponent} from './app.component';

@Component({template: '<h1>First page</h1>'})
class FirstPage {}
const $ready = signal(false);
@Component({template: '@if ($loaded()) { <h1>Loaded page</h1> } @else { <p>Loading</p> }'})
class LoadingPage { readonly $loaded = $ready; }

describe('AppComponent', () => {
  beforeEach(async () => {
    $ready.set(false);
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        {provide: AuthenticationService, useValue: {isLoggedIn$: of(false), resetInactivityTimer: () => undefined}},
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{path: 'first', component: FirstPage}, {path: 'loaded', component: LoadingPage}]),
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

});
