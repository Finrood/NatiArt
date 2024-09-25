import {Directive, ElementRef, HostListener, Renderer2} from '@angular/core';
import {NgControl} from "@angular/forms";

@Directive({
  selector: '[CpfFormat]',
  standalone: true
})
export class CpfFormatDirectiveDirective {
  constructor(
    private el: ElementRef,
    private renderer: Renderer2,
    private control: NgControl
  ) {}

  @HostListener('input', ['$event'])
  onInputChange(event: Event) {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');

    if (value.length > 11) {
      value = value.slice(0, 11);
    } else if (value.length > 9) {
      value = `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6, 9)}-${value.slice(9)}`;
    } else if (value.length > 6) {
      value = `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6)}`;
    } else if (value.length > 3) {
      value = `${value.slice(0, 3)}.${value.slice(3)}`;
    }

    this.updateValue(value);
  }

  @HostListener('blur')
  onBlur() {
    const value = this.el.nativeElement.value;
    if (value.length === 11 && value.indexOf('-') === -1 && value.indexOf('.') === -1) {
      this.updateValue(`${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6, 9)}-${value.slice(9)}`);
    }
  }

  private updateValue(value: string) {
    this.renderer.setProperty(this.el.nativeElement, 'value', value);
    this.control.control?.setValue(value, { emitEvent: false });
    this.control.control?.markAsTouched();
  }
}
