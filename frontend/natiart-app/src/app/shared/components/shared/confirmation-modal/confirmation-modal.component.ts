// START OF FILE: src/app/shared/components/shared/confirmation-modal/confirmation-modal.component.ts
import {Component, EventEmitter, HostListener, Input, OnChanges, OnDestroy, Output, SimpleChanges} from '@angular/core';

import {ButtonComponent} from "../../button.component";

@Component({
  selector: 'app-confirmation-modal',
  standalone: true, // Make sure it's standalone
  imports: [ButtonComponent],   // Import NgIf
  template: `
    @if (isOpen) {
      <div
        class="fixed inset-0 bg-black bg-opacity-60 flex items-center justify-center z-50 transition-opacity duration-300 ease-out"
        id="modal-overlay"
        (click)="onCancel()"> <!-- Optional: Close on overlay click -->
        <div class="bg-surface rounded-lg shadow-xl w-full max-w-md m-4 p-6 transform transition-transform duration-300 ease-out scale-95 opacity-0"
          role="dialog" aria-modal="true" aria-labelledby="confirmation-modal-title" tabindex="-1"
          [class.scale-100]="isOpen" [class.opacity-100]="isOpen"
          (click)="$event.stopPropagation()"> <!-- Prevent closing when clicking inside modal -->
          <div class="text-center">
            <!-- Title -->
            <h3 id="confirmation-modal-title" class="text-xl font-semibold leading-6 text-secondary-dark mb-3">{{ title }}</h3>
            <!-- Message -->
            <div class="mt-2 mb-6">
              <p class="text-sm text-secondary">{{ message }}</p>
            </div>
            <!-- Buttons -->
            <div class="flex flex-col sm:flex-row-reverse gap-3">
              <!-- Confirm Button (Primary) -->
              <app-button (click)="onConfirm()" type="button" color="primary" size="md" [block]="true">
                {{ confirmText }}
              </app-button>
              <!-- Cancel Button (Secondary - Bordered) -->
              <app-button (click)="onCancel()" type="button" color="secondary" size="md" [block]="true">
                {{ cancelText }}
              </app-button>
            </div>
          </div>
        </div>
      </div>
    }
    `,
  // Add styleUrl if you have separate CSS, otherwise keep empty/remove
  // styleUrl: './confirmation-modal.component.css'
})
export class ConfirmationModalComponent implements OnChanges, OnDestroy {
  @Input() isOpen = false;
  @Input() title = 'Confirm Action';
  @Input() message = 'Are you sure you want to perform this action?';
  @Input() confirmText = 'Confirm';
  @Input() cancelText = 'Cancel';
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  private previouslyFocused: HTMLElement | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['isOpen']) return;
    if (this.isOpen) {
      this.previouslyFocused = document.activeElement as HTMLElement | null;
      document.body.style.overflow = 'hidden';
      setTimeout(() => document.querySelector<HTMLElement>('[role="dialog"] button')?.focus());
    } else {
      this.restoreFocus();
    }
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.isOpen) {
      event.preventDefault();
      this.onCancel();
    }
  }

  private restoreFocus(): void {
    document.body.style.overflow = '';
    this.previouslyFocused?.focus();
    this.previouslyFocused = null;
  }

  ngOnDestroy(): void {
    this.restoreFocus();
  }

  onConfirm(): void {
    this.confirm.emit();
    // No need to set isOpen = false here, parent should handle it if needed
  }

  onCancel(): void {
    this.cancel.emit();
    // No need to set isOpen = false here, parent should handle it if needed
  }
}
