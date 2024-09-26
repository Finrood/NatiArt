import {Directive, ElementRef, HostListener, Renderer2} from '@angular/core';
import {NgControl} from '@angular/forms';

@Directive({
  standalone: true,
  selector: '[cepFormat]'
})
export class CepFormatDirective {
  constructor(
    private el: ElementRef,
    private renderer: Renderer2,
    private control: NgControl
  ) {
  }

  @HostListener('input', ['$event'])
  onInputChange(event: Event) {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');

    if (value.length > 8) {
      value = value.slice(0, 8);
    } else if (value.length > 5) {
      value = `${value.slice(0, 5)}-${value.slice(5)}`;
    }

    this.updateValue(value);
  }

  @HostListener('blur')
  onBlur() {
    const value = this.el.nativeElement.value;
    if (value.length === 6 && value.indexOf('-') === -1) {
      this.updateValue(`${value.slice(0, 5)}-${value.slice(5)}`);
    }
  }

  private updateValue(value: string) {
    this.renderer.setProperty(this.el.nativeElement, 'value', value);
    this.control.control?.setValue(value, {emitEvent: false});
    this.control.control?.markAsTouched();
  }
}
