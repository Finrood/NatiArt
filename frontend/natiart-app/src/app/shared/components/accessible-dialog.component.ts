import {AfterViewInit, Component, ElementRef, EventEmitter, inject, input, Input, OnDestroy, Output, ViewChild} from '@angular/core';
import {DOCUMENT} from '@angular/common';

@Component({selector: 'app-accessible-dialog', template: `
  <dialog #dialog aria-modal="true" [attr.aria-label]="label" (cancel)="cancel($event)" (click)="backdrop($event)">
    @if ($showClose()) {
      <button type="button" class="dialog-close" aria-label="Close" i18n-aria-label (click)="dismiss.emit()">
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="m6 6 12 12M6 18 18 6" stroke-width="1.5" stroke-linecap="round"/></svg>
      </button>
    }
    <ng-content></ng-content>
  </dialog>`, styles: `
  dialog { margin: auto; inset: 0; width: max-content; padding: 0; border: 0; border-radius: .75rem; background: transparent;
    max-width: calc(100vw - 2rem); max-height: calc(100vh - 2rem); overflow: auto; }
  dialog::backdrop { background: rgb(0 0 0 / .5); }
  dialog:focus-visible { outline: 2px solid rgb(var(--primary)); outline-offset: 2px; }
  .dialog-close { position: sticky; top: .5rem; float: right; z-index: 20; display: grid; place-items: center; width: 2.75rem; height: 2.75rem; margin: .5rem .5rem -3.25rem 0; color: rgb(var(--primary)); background: rgb(var(--surface)); border: 1px solid rgb(var(--primary-light) / .5); border-radius: .375rem; }
  .dialog-close svg { width: 1.25rem; height: 1.25rem; }
`})
export class AccessibleDialogComponent implements AfterViewInit, OnDestroy {
  private static openCount: number = 0;
  private static previousOverflow: string = '';
  private readonly _document = inject(DOCUMENT);
  private previousFocus: HTMLElement | null = null;
  private opened: boolean = false;
  @Input({required: true}) label!: string;
  readonly $showClose = input<boolean>(false, {alias: 'showClose'});
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
