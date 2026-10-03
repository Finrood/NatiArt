import {Component, EventEmitter, signal, Input, Output} from '@angular/core';
import {ButtonComponent} from '../../button.component';
import {AccessibleDialogComponent} from '../../accessible-dialog.component';

@Component({selector: 'app-confirmation-modal', imports: [ButtonComponent, AccessibleDialogComponent], template: `
  @if (isOpen) {
    <app-accessible-dialog [label]="title" (dismiss)="onCancel()">
      <div class="bg-surface rounded-lg shadow-xl w-full max-w-md p-6">
        <h3 class="text-xl font-semibold text-secondary-dark mb-3">{{ title }}</h3>
        <p class="text-sm text-secondary mb-6">{{ message }}</p>
        <div class="flex flex-col sm:flex-row-reverse gap-3">
          <app-button (click)="onCancel()" type="button" color="secondary" [block]="true">{{ cancelText }}</app-button>
          <app-button (click)="onConfirm()" type="button" color="primary" [block]="true">{{ confirmText }}</app-button>
        </div>
      </div>
    </app-accessible-dialog>
  }`})
export class ConfirmationModalComponent {
  readonly $isOpen = signal(false);
  @Input() get isOpen(): boolean { return this.$isOpen(); }
  set isOpen(value: boolean) { this.$isOpen.set(value); }
  @Input() title = $localize`Confirm Action`;
  @Input() message = $localize`Are you sure you want to perform this action?`;
  @Input() confirmText = $localize`Confirm`;
  @Input() cancelText = $localize`Cancel`;
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  onConfirm(): void { this.confirm.emit(); }
  onCancel(): void { this.cancel.emit(); }
}
