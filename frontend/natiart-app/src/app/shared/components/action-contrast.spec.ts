import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ButtonComponent} from './button.component';

@Component({imports: [ButtonComponent], template: `
  <div class="bg-background">
    <p class="text-primary">Price</p><p class="text-secondary-light">Description</p>
    <app-button>Purchase</app-button><app-button color="success">Fulfill</app-button>
    <app-button color="danger">Cancel</app-button><app-button color="warning">Review</app-button>
    <app-button color="info">Details</app-button>
    <span class="bg-primary-dark text-primary-contrast">Primary hover</span>
  </div>`})
class ContrastActions {}

function luminance(color: string): number {
  const channels: number[] = color.match(/[\d.]+/g)!.slice(0, 3).map(Number);
  return channels.reduce((sum: number, channel: number, index: number): number => {
    const value: number = channel / 255;
    return sum + (value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
      * [0.2126, 0.7152, 0.0722][index];
  }, 0);
}

describe('Enabled action and body text contrast', () => {
  it('meets ordinary-text contrast with the rendered theme and button styles', async () => {
    await TestBed.configureTestingModule({imports: [ContrastActions]}).compileComponents();
    const fixture = TestBed.createComponent(ContrastActions);
    fixture.detectChanges();
    const surface: string = getComputedStyle(fixture.nativeElement.querySelector('div')).backgroundColor;
    for (const element of fixture.nativeElement.querySelectorAll('p, button, span') as NodeListOf<HTMLElement>) {
      const style: CSSStyleDeclaration = getComputedStyle(element);
      const background: string = element.tagName === 'P' ? surface : style.backgroundColor;
      const values: number[] = [luminance(style.color), luminance(background)].sort((a, b) => b - a);
      expect((values[0] + 0.05) / (values[1] + 0.05)).withContext(element.textContent || '').toBeGreaterThanOrEqual(4.5);
    }
    fixture.destroy();
  });
});
