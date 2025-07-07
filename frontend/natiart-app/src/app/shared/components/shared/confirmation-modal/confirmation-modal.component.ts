// START OF FILE: src/app/shared/components/shared/confirmation-modal/confirmation-modal.component.ts
import {Component, EventEmitter, Input, Output} from '@angular/core';
import {NgIf} from '@angular/common';
import {ButtonComponent} from "../../button.component";

@Component({
  selector: 'app-confirmation-modal',
  standalone: true, // Make sure it's standalone
  imports: [NgIf, ButtonComponent],   // Import NgIf
  template: `
    <div *ngIf="isOpen"
         class="fixed inset-0 bg-black bg-opacity-60 flex items-center justify-center z-50 transition-opacity duration-300 ease-out"
         id="modal-overlay"
         (click)="onCancel()"> <!-- Optional: Close on overlay click -->

      <div class="bg-surface rounded-lg shadow-xl w-full max-w-md m-4 p-6 transform transition-transform duration-300 ease-out scale-95 opacity-0"
           [class.scale-100]="isOpen" [class.opacity-100]="isOpen"
           (click)="$event.stopPropagation()"> <!-- Prevent closing when clicking inside modal -->

        <div class="text-center">
          <!-- Title -->
          <h3 class="text-xl font-semibold leading-6 text-secondary-dark mb-3">{{ title }}</h3>

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
  `,
  // Add styleUrl if you have separate CSS, otherwise keep empty/remove
  // styleUrl: './confirmation-modal.component.css'
})
export class ConfirmationModalComponent {
  @Input() isOpen = false;
  @Input() title = 'Confirm Action';
  @Input() message = 'Are you sure you want to perform this action?';
  @Input() confirmText = 'Confirm';
  @Input() cancelText = 'Cancel';
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  onConfirm(): void {
    this.confirm.emit();
    // No need to set isOpen = false here, parent should handle it if needed
  }

  onCancel(): void {
    this.cancel.emit();
    // No need to set isOpen = false here, parent should handle it if needed
  }
}
