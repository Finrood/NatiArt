import {TestBed} from '@angular/core/testing';
import {OrderJourneyComponent} from './order-journey.component';
import {OrderDto} from '../../product/models/order.model';

describe('Order journey', (): void => {
  const order: OrderDto = {firstname: 'Buyer', lastname: 'Example', email: 'buyer@example.test',
    country: 'BR', state: 'SP', city: 'City', neighborhood: 'Area', street: 'Street', zipCode: '01001000', items: []};
  it('keeps unpaid preparation in the future and never invents a date for legacy milestones', (): void => {
    const fixture = TestBed.createComponent(OrderJourneyComponent);
    fixture.componentRef.setInput('order', {...order, status: 'PENDING'}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('li[data-reached="true"]').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Preparation starts after payment');
    fixture.componentRef.setInput('order', {...order, status: 'SHIPPED'}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('li[data-reached="true"]').length).toBe(3);
    expect([...fixture.nativeElement.querySelectorAll('li[data-reached="true"]')].every((step: unknown): boolean => (step as HTMLElement).textContent!.includes('Recorded milestone'))).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('time').length).toBe(0);
  });
  it('shows only safe carrier links and protects links to new windows', (): void => {
    const fixture = TestBed.createComponent(OrderJourneyComponent);
    fixture.componentRef.setInput('order', {...order, status: 'SHIPPED', trackingCode: 'BR123', trackingUrl: 'javascript:alert(1)'}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a')).toBeNull();
    fixture.componentRef.setInput('order', {...order, status: 'SHIPPED', trackingCode: 'BR123', trackingUrl: 'https://carrier.example.test/BR123'}); fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    expect(link.href).toBe('https://carrier.example.test/BR123'); expect(link.rel).toContain('noopener');
    expect(fixture.nativeElement.textContent).toContain('BR123');
  });
  it('renders cancellation separately from the preparation journey', (): void => {
    const fixture = TestBed.createComponent(OrderJourneyComponent);
    fixture.componentRef.setInput('order', {...order, status: 'CANCELLED', cancelledAt: '2026-10-09T12:00:00Z'}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ol')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Order cancelled');
    expect(fixture.nativeElement.querySelector('time')).not.toBeNull();
  });
});
