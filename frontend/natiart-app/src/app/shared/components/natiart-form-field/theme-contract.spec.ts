import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {NatiartFormFieldComponent} from './natiart-form-field.component';

@Component({imports: [NatiartFormFieldComponent, ReactiveFormsModule], template: `
  <div class="font-sans" [formGroup]="form">
    <h1 class="font-serif">Porcelain</h1>
    <app-natiart-form-field label="Name" controlName="name" [form]="form">
      <input formControlName="name" class="form-input" id="name">
    </app-natiart-form-field>
    <div class="bg-black/50" id="overlay">Overlay</div>
  </div>`})
class ThemeForm {
  readonly form = new FormGroup({name: new FormControl('Art', Validators.required)});
}

describe('Theme computed styles', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [ThemeForm]}).compileComponents();});
  it('styles projected input borders, focus and error, and loads the local fonts', async () => {
    const fixture = TestBed.createComponent(ThemeForm);
    fixture.detectChanges();
    await document.fonts.load('400 16px Poppins');
    await document.fonts.load('400 16px "Playfair Display"');
    expect(document.fonts.check('400 16px Poppins')).toBeTrue();
    expect(document.fonts.check('400 16px "Playfair Display"')).toBeTrue();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    const normal = getComputedStyle(input).borderTopColor;
    expect(getComputedStyle(input).borderTopWidth).toBe('1px');
    expect(parseFloat(getComputedStyle(input).paddingLeft)).toBeGreaterThan(0);
    input.focus();
    await new Promise<void>((resolve) => setTimeout(resolve, 250));
    const focus = getComputedStyle(input).borderTopColor;
    expect(focus).not.toBe(normal);
    expect(getComputedStyle(input).boxShadow).not.toBe('none');
    input.value = '';
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    input.focus();
    await new Promise<void>((resolve) => setTimeout(resolve, 250));
    expect(getComputedStyle(input).borderTopColor).not.toBe(focus);
    expect(fixture.nativeElement.querySelector('.has-error')).not.toBeNull();
    fixture.destroy();
  });

  it('retains local font families, form spacing and translucent overlays in mobile and desktop viewports', async () => {
    const fixture = TestBed.createComponent(ThemeForm);
    fixture.detectChanges();
    const css: string = Array.from(document.styleSheets).flatMap((sheet: CSSStyleSheet) =>
      Array.from(sheet.cssRules).map((rule: CSSRule) => rule.cssText)).join('\n');
    for (const width of [360, 1280]) {
      const frame = document.createElement('iframe');
      frame.style.width = width + 'px';
      frame.style.height = '500px';
      frame.style.border = '0';
      document.body.appendChild(frame);
      const doc = frame.contentDocument!;
      doc.open();
      doc.write('<!doctype html><html><head><style>' + css + '</style></head><body>' + fixture.nativeElement.innerHTML + '</body></html>');
      doc.close();
      await doc.fonts.ready;
      const win = frame.contentWindow!;
      expect(win.innerWidth).toBe(width);
      expect(win.getComputedStyle(doc.querySelector('input')!).fontFamily).toContain('Poppins');
      expect(win.getComputedStyle(doc.querySelector('h1')!).fontFamily).toContain('Playfair Display');
      expect(win.getComputedStyle(doc.querySelector('input')!).borderTopWidth).toBe('1px');
      expect(win.getComputedStyle(doc.querySelector('#overlay')!).backgroundColor).toMatch(/(?:0\.5|50%)/);
      frame.remove();
    }
    fixture.destroy();
  });
});
