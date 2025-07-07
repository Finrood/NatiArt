import {Directive, HostListener, Renderer2} from '@angular/core';
import {NgControl} from '@angular/forms';

@Directive({
  selector: '[cpfFormat]'
})
export class CpfFormatDirective {
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
    this.formatCpf(event);
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

  private formatCpf(event: InputEvent) {
    const input = event.target as HTMLInputElement;
    const cleanValue = input.value.replace(/\D/g, '').slice(0, 11); // Only numbers, max length 11
    const formattedValue = this.applyFormat(cleanValue);

    this.updateValue(input, formattedValue, cleanValue);
  }

  private applyFormat(value: string): string {
    if (value.length <= 3) {
      return value;
    } else if (value.length <= 6) {
      return `${value.slice(0, 3)}.${value.slice(3)}`;
    } else if (value.length <= 9) {
      return `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6)}`;
    } else {
      return `${value.slice(0, 3)}.${value.slice(3, 6)}.${value.slice(6, 9)}-${value.slice(9)}`;
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
