import {AfterViewInit, Component, ElementRef, EventEmitter, inject, Input, OnDestroy, Output, ViewChild} from '@angular/core';
import {DOCUMENT} from '@angular/common';

@Component({selector: 'app-accessible-dialog', template: `
  <dialog #dialog aria-modal="true" [attr.aria-label]="label" (cancel)="cancel($event)" (click)="backdrop($event)">
    <ng-content></ng-content>
  </dialog>`, styles: `
  dialog { padding: 0; border: 0; border-radius: .75rem; background: transparent;
    max-width: calc(100vw - 2rem); max-height: calc(100vh - 2rem); overflow: auto; }
  dialog::backdrop { background: rgb(0 0 0 / .5); }
  dialog:focus-visible { outline: 2px solid rgb(var(--primary)); outline-offset: 2px; }
`})
export class AccessibleDialogComponent implements AfterViewInit, OnDestroy {
  private static openCount: number = 0;
  private static previousOverflow: string = '';
  private readonly _document = inject(DOCUMENT);
  private previousFocus: HTMLElement | null = null;
  private opened: boolean = false;
  @Input({required: true}) label!: string;
  @Output() dismiss = new EventEmitter<void>();
  @ViewChild('dialog') dialog!: ElementRef<HTMLDialogElement>;

  ngAfterViewInit(): void {
    this.previousFocus = this._document.activeElement as HTMLElement | null;
    this.dialog.nativeElement.showModal();
    this.opened = true;
    if (AccessibleDialogComponent.openCount++ === 0) {
      AccessibleDialogComponent.previousOverflow = this._document.body.style.overflow;
      this._document.body.style.overflow = 'hidden';
    }
  }

  cancel(event: Event): void {
    event.preventDefault();
    this.dismiss.emit();
  }

  backdrop(event: MouseEvent): void {
    if (event.target !== this.dialog.nativeElement) return;
    const rect: DOMRect = this.dialog.nativeElement.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) {
      this.dismiss.emit();
    }
  }

  ngOnDestroy(): void {
    if (!this.opened) return;
    this.dialog.nativeElement.close();
    this.opened = false;
    if (--AccessibleDialogComponent.openCount === 0) {
      this._document.body.style.overflow = AccessibleDialogComponent.previousOverflow;
    }
    if (this.previousFocus?.isConnected) this.previousFocus.focus();
    this.previousFocus = null;
  }
}
