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
    <input class="form-input" placeholder="Your email" aria-label="Email">
    <app-button color="link">Show password</app-button>
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
      const background: string = element.tagName === 'P' || style.backgroundColor === 'rgba(0, 0, 0, 0)' ? surface : style.backgroundColor;
      const values: number[] = [luminance(style.color), luminance(background)].sort((a, b) => b - a);
      expect((values[0] + 0.05) / (values[1] + 0.05)).withContext(element.textContent || '').toBeGreaterThanOrEqual(4.5);
    }
    fixture.destroy();
  });
  it('keeps control borders distinct and placeholders readable on the rendered surfaces', async () => {
    await TestBed.configureTestingModule({imports: [ContrastActions]}).compileComponents();
    const fixture = TestBed.createComponent(ContrastActions); fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    const style: CSSStyleDeclaration = getComputedStyle(input);
    const surface: string = getComputedStyle(fixture.nativeElement.querySelector('div')).backgroundColor;
    const blend = (foreground: string, background: string): string => {
      // Let the browser resolve CSS color()/color-mix() and alpha compositing.
      const canvas: HTMLCanvasElement = document.createElement('canvas'); canvas.width = canvas.height = 1;
      const context: CanvasRenderingContext2D = canvas.getContext('2d')!;
      context.fillStyle = background; context.fillRect(0, 0, 1, 1);
      context.fillStyle = foreground; context.fillRect(0, 0, 1, 1);
      return 'rgb(' + Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3).join(',') + ')';
    };
    const background: string = blend(style.backgroundColor, surface);
    const ratio = (foreground: string, against: string): number => {
      const values: number[] = [luminance(foreground), luminance(against)].sort((a, b) => b - a);
      return (values[0] + .05) / (values[1] + .05);
    };
    const border: string = blend(style.borderTopColor, background);
    expect(ratio(border, background)).toBeGreaterThanOrEqual(3);
    expect(ratio(border, surface)).toBeGreaterThanOrEqual(3);
    expect(ratio(blend(getComputedStyle(input, '::placeholder').color, background), background)).toBeGreaterThanOrEqual(4.5);
    const link: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-link');
    link.focus();
    expect(link.matches(':focus-visible')).toBeTrue();
    expect(getComputedStyle(link).outlineStyle).toBe('solid');
    expect(parseFloat(getComputedStyle(link).outlineWidth)).toBeGreaterThanOrEqual(2);
    expect(ratio(getComputedStyle(link).outlineColor, surface)).toBeGreaterThanOrEqual(3);
    fixture.destroy();
  });
});
