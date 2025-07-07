import {Directive, HostListener, Renderer2} from '@angular/core';
import {NgControl} from '@angular/forms';

@Directive({
  selector: '[cepFormat]'
})
export class CepFormatDirective {
  constructor(
    private renderer: Renderer2,
    private control: NgControl
  ) {}

  @HostListener('input', ['$event'])
  onInput(event: InputEvent) {
    this.formatCep(event);
  }

  private formatCep(event: InputEvent) {
    const input = event.target as HTMLInputElement;
    const cleanValue = input.value.replace(/\D/g, '').slice(0, 8);
    const formattedValue = this.applyFormat(cleanValue);

    this.updateValue(input, formattedValue, cleanValue);
  }

  private applyFormat(value: string): string {
    return value.length > 5 ? `${value.slice(0, 5)}-${value.slice(5)}` : value;
  }

  private updateValue(input: HTMLInputElement, formattedValue: string, cleanValue: string) {
    const previousValue = input.value;
    if (previousValue !== formattedValue) {
      this.renderer.setProperty(input, 'value', formattedValue);
      this.control.control?.setValue(cleanValue, {
        emitEvent: true,
        emitModelToViewChange: false
      });

      this.adjustCursorPosition(input, previousValue, formattedValue);
    }
  }

  private adjustCursorPosition(input: HTMLInputElement, oldValue: string, newValue: string) {
    const pos = input.selectionStart ?? newValue.length;
    const diff = newValue.length - oldValue.length;
    const newCursorPos = Math.max(0, pos + diff);

    requestAnimationFrame(() => {
      input.setSelectionRange(newCursorPos, newCursorPos);
    });
  }
}
