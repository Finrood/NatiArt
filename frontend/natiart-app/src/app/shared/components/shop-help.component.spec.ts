import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {ShopHelpComponent} from './shop-help.component';
import {StoreContactService} from '../service/store-contact.service';

describe('Shop help', (): void => {
  it('offers verified guest access and account history without promising a delivery deadline', (): void => {
    TestBed.configureTestingModule({providers: [provideRouter([]), {provide: StoreContactService, useValue: {email: 'atelier@example.test'}}]});
    const fixture = TestBed.createComponent(ShopHelpComponent); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('a[href="/claim-orders"]')).not.toBeNull();
    expect(root.querySelector('a[href="/account"]')).not.toBeNull();
    expect(root.querySelector('a[href="mailto:atelier@example.test"]')).not.toBeNull();
    expect(root.textContent).toContain('Creating an account is optional');
    expect(root.querySelectorAll('details').length).toBe(5);
  });
});
