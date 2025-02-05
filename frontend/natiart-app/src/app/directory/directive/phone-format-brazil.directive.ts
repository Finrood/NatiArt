import {Directive, HostListener, Renderer2} from '@angular/core';
import {NgControl} from '@angular/forms';

@Directive({
  selector: '[appPhoneFormatBrazil]'
})
export class PhoneFormatBrazilDirective {
  private readonly navigationKeys = new Set([
    'Backspace', 'Delete', 'Tab', 'Escape', 'Enter',
    'Home', 'End', 'ArrowLeft', 'ArrowRight'
  ]);

  constructor(
    private renderer: Renderer2,
    private control: NgControl
  ) {}

  @HostListener('input', ['$event'])
  onInput(event: InputEvent) {
    this.formatPhone(event);
  }

  @HostListener('keydown', ['$event'])
  onKeyDown(event: KeyboardEvent) {
    if (this.navigationKeys.has(event.key) || (event.ctrlKey && ['a', 'c', 'v', 'x'].includes(event.key.toLowerCase()))) {
      return;
    }

    if (isNaN(Number(event.key))) {
      event.preventDefault();
    }
  }

  private formatPhone(event: InputEvent) {
    const input = event.target as HTMLInputElement;
    const cleanValue = input.value.replace(/\D/g, '').slice(0, 11); // Remove non-numeric chars
    const formattedValue = this.applyFormat(cleanValue);

    this.updateValue(input, formattedValue);
  }

  private applyFormat(value: string): string {
    if (value.length <= 2) {
      return `(${value}`;
    } else if (value.length <= 7) {
      return `(${value.slice(0, 2)}) ${value.slice(2)}`;
    } else {
      return `(${value.slice(0, 2)}) ${value.slice(2, 7)}-${value.slice(7)}`;
    }
  }

  private updateValue(input: HTMLInputElement, formattedValue: string) {
    const previousValue = input.value;
    if (previousValue !== formattedValue) {
      this.renderer.setProperty(input, 'value', formattedValue);
      this.control.control?.setValue(formattedValue.replace(/\D/g, ''), {
        emitEvent: false,
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
