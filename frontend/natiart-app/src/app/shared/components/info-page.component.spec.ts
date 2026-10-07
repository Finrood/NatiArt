import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter, Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {routes} from '../../app.routes';

@Component({template: '<h1>Collections</h1>'})
class CollectionsDestination {}

describe('Informational navigation', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({providers: [provideRouter(routes.map(route => route.path === 'products' ? {path: 'products', component: CollectionsDestination} : route))]}).compileComponents();
  });
  it('updates the reused page for each destination and renders an explicit unknown-page result', async () => {
    const harness = await RouterTestingHarness.create();
    for (const path of ['about', 'contact', 'faq', 'shipping-returns']) {
      await harness.navigateByUrl('/' + path);
      expect(TestBed.inject(Router).url).toBe('/products');
      expect(harness.routeNativeElement!.textContent).toContain('Collections');
    }
    for (const [path, title] of [['care-instructions', 'Care instructions'], ['missing-page', 'Page not found']]) {
      await harness.navigateByUrl('/' + path);
      expect(harness.routeNativeElement!.querySelector('h1')!.textContent).toBe(title);
      expect(harness.routeNativeElement!.querySelector('a')!.getAttribute('href')).toBe('/products');
    }
    expect(TestBed.inject(Router).url).toBe('/not-found');
    const catalog = routes.find((route) => route.path === 'products')!;
    expect(catalog.canActivate).toBeUndefined();
    expect((await catalog.loadComponent!()) as unknown).toBeDefined();
    harness.fixture.destroy();
  });
});
