import {Type} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {CategoryManagementComponent} from '../../product/components/admin/admin-category-management/admin-category-management.component';
import {PackageManagementComponent} from '../../product/components/admin/admin-package-management/admin-package-management.component';
import {environment} from '../../../environments/environment';

for (const resource of [
  {component: CategoryManagementComponent, path: 'categories', title: 'Category', extra: {}},
  {component: PackageManagementComponent, path: 'packages', title: 'Package', extra: {height: 1, width: 1, depth: 1}},
]) {
  describe(resource.title + ' rendered writes', () => {
    let http: HttpTestingController;
    let fixture: ComponentFixture<unknown>;
    const url: string = `${environment.api.product.url}/${resource.path}`;
    function click(label: string): void {
      const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
        .find((item: HTMLButtonElement) => item.textContent?.trim() === label)!;
      expect(button).withContext(label).toBeDefined();
      button.click();
      fixture.detectChanges();
    }
    function fill(label: string): void {
      for (const [control, value] of Object.entries({label, ...resource.extra})) {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[formControlName="${control}"]`);
        input.value = String(value);
        input.dispatchEvent(new Event('input'));
      }
      fixture.detectChanges();
    }
    function submit(): void {
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
      fixture.detectChanges();
    }
    beforeEach(async () => {
      await TestBed.configureTestingModule({imports: [resource.component], providers: [provideHttpClient(),
        provideHttpClientTesting(), provideRouter([])]}).compileComponents();
      http = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(resource.component as Type<unknown>);
      fixture.detectChanges();
      http.expectOne((request) => request.url === `${environment.api.product.url}/admin/${resource.path}/page` && request.params.get('page') === '0' && request.params.get('size') === '20').flush({items: [{id: 'used', label: 'Existing art', active: true, ...resource.extra}], page: 0, size: 20, total: 1, hasNext: false});
      fixture.detectChanges();
    });
    afterEach(() => {fixture.destroy(); http.verify();});

    it('sends one delayed write and visibly restores retry with input after failure', () => {
      click('Add New ' + resource.title);
      fill('My art');
      submit();
      submit();
      const writes = http.match((request) => request.method === 'POST');
      expect(writes.length).toBe(1);
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeTrue();
      writes[0].flush('duplicate', {status: 409, statusText: 'Conflict'});
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeFalse();
      expect(fixture.nativeElement.querySelector('input[formControlName="label"]').value).toBe('My art');
      expect(fixture.nativeElement.textContent).toContain('with this label already exists');
      submit();
      http.expectOne((request) => request.method === 'POST').flush({id: 'new', label: 'My art', ...resource.extra});
      http.expectOne((request) => request.method === 'GET').flush({items: [], page: 0, size: 20, total: 0, hasNext: false});
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('form')).toBeNull();
    });

    it('keeps a newer modal intact when the previous edit completes', () => {
      click('Add New ' + resource.title);
      fill('Old art');
      submit();
      const pending = http.expectOne((request) => request.method === 'POST');
      click('Cancel');
      click('Add New ' + resource.title);
      fill('New art');
      pending.flush({id: 'old', label: 'Old art', ...resource.extra});
      http.expectOne((request) => request.method === 'GET').flush({items: [], page: 0, size: 20, total: 0, hasNext: false});
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('input[formControlName="label"]').value).toBe('New art');
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeFalse();
    });

    it('names the record in deletion confirmation and cancel sends no request', () => {
      const confirm = spyOn(window, 'confirm').and.returnValue(false);
      click('Delete');
      expect(confirm.calls.mostRecent().args[0]).toContain('Existing art');
      http.expectNone((request) => request.method === 'DELETE');
    });
  });
}
