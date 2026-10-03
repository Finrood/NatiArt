import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { CategoryManagementComponent } from './admin-category-management.component';

describe('CategoryManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('guards rendered duplicate toggles and lets another category proceed', () => {
    const fixture = TestBed.createComponent(CategoryManagementComponent);
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.method === 'GET').flush({items: [
      {id: 'a', label: 'First', active: true}, {id: 'b', label: 'Second', active: true}
    ], page: 0, size: 20, total: 2, hasNext: false});
    fixture.detectChanges();
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const hide: HTMLButtonElement[] = buttons.filter((b) => b.textContent?.includes('Hide'));
    hide[0].click(); hide[0].click();
    const first = http.expectOne((r) => r.method === 'PATCH' && r.url.includes('/a/'));
    fixture.detectChanges();
    expect(hide[0].disabled).toBeTrue();
    expect(hide[1].disabled).toBeFalse();
    hide[1].click();
    http.expectOne((r) => r.method === 'PATCH' && r.url.includes('/b/')).flush({id: 'b', label: 'Second', active: false});
    const cancelledRefresh = http.expectOne((r) => r.method === 'GET');
    first.flush({id: 'a', label: 'First', active: false});
    expect(cancelledRefresh.cancelled).toBeTrue();
    http.expectOne((r) => r.method === 'GET').flush({items: [{id: 'a', label: 'First', active: false}, {id: 'b', label: 'Second', active: false}], page: 0, size: 20, total: 2, hasNext: false});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Hide');
    expect(fixture.componentInstance.isVisibilityPending('a')).toBeFalse();
    http.verify();
  });

  it('reenables the rendered toggle after error for one retry', () => {
    const fixture = TestBed.createComponent(CategoryManagementComponent);
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.method === 'GET').flush({items: [{id: 'a', label: 'First', active: true}], page: 0, size: 20, total: 1, hasNext: false});
    fixture.detectChanges();
    const button: HTMLButtonElement = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((b) => b.textContent?.includes('Hide'))!;
    button.click();
    http.expectOne((r) => r.method === 'PATCH').flush('Unavailable', {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect(button.disabled).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Unable to change category visibility');
    button.click();
    http.expectOne((r) => r.method === 'PATCH').flush({id: 'a', label: 'First', active: false});
    http.expectOne((r) => r.method === 'GET').flush({items: [{id: 'a', label: 'First', active: false}], page: 0, size: 20, total: 1, hasNext: false});
    http.verify();
  });

  it('releases pending toggles when destroyed', () => {
    const fixture = TestBed.createComponent(CategoryManagementComponent);
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.method === 'GET').flush({items: [{id: 'a', label: 'First', active: true}], page: 0, size: 20, total: 1, hasNext: false});
    fixture.componentInstance.toggleCategoryVisibility({id: 'a', label: 'First', active: true});
    const request = http.expectOne((r) => r.method === 'PATCH');
    fixture.destroy();
    expect(request.cancelled).toBeTrue();
    expect(fixture.componentInstance.isVisibilityPending('a')).toBeFalse();
    http.verify();
  });

  it('should create' , () => {
    const fixture = TestBed.createComponent(CategoryManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
