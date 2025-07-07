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

    this.updateValue(input, formattedValue, cleanValue);
  }

  private applyFormat(value: string): string {
    // (XX) XXXX-XXXX or (XX) XXXXX-XXXX
    if (value.length <= 2) {
      return value; // Just the DDD
    } else if (value.length <= 6) {
      return `(${value.slice(0, 2)}) ${value.slice(2)}`;
    } else if (value.length <= 10) {
      // Handles 8-digit numbers (e.g., 1234-5678)
      return `(${value.slice(0, 2)}) ${value.slice(2, 6)}-${value.slice(6)}`;
    } else {
      // Handles 9-digit numbers (e.g., 12345-6789)
      return `(${value.slice(0, 2)}) ${value.slice(2, 7)}-${value.slice(7)}`;
    }
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
