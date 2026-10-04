import {Component, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {AccessibleDialogComponent} from './accessible-dialog.component';
import {ConfirmationModalComponent} from './shared/confirmation-modal/confirmation-modal.component';

@Component({imports: [AccessibleDialogComponent, ConfirmationModalComponent], template: `
  <button id="opener" (click)="$open.set(true)">Open</button><button id="outside">Outside</button>
  @if ($open()) { <app-accessible-dialog label="Editor" (dismiss)="$open.set(false)"><input aria-label="Name"><button>Done</button></app-accessible-dialog> }
  <app-confirmation-modal [isOpen]="$confirm()" title="Remove art" (cancel)="$confirm.set(false)"></app-confirmation-modal>`})
class DialogJourney {
  readonly $open = signal(false);
  readonly $confirm = signal(false);
}

describe('Native modal keyboard contract', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [DialogJourney]}).compileComponents();});
  it('contains focus, makes background inert, dismisses on Escape and restores opener/scroll state', () => {
    const fixture = TestBed.createComponent(DialogJourney);
    fixture.detectChanges();
    const opener: HTMLButtonElement = fixture.nativeElement.querySelector('#opener');
    const outside: HTMLButtonElement = fixture.nativeElement.querySelector('#outside');
    document.body.style.overflow = 'clip';
    opener.focus();
    opener.click();
    fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(dialog.matches(':modal')).toBeTrue();
    expect(dialog.getAttribute('aria-label')).toBe('Editor');
    expect(dialog.contains(document.activeElement)).toBeTrue();
    outside.focus();
    expect(dialog.contains(document.activeElement)).toBeTrue();
    expect(document.body.style.overflow).toBe('hidden');
    const cancel = new Event('cancel', {cancelable: true});
    dialog.dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(opener);
    expect(document.body.style.overflow).toBe('clip');
    document.body.style.overflow = '';
    fixture.destroy();
  });
  it('keeps scroll locked until the last nested modal closes and uses the safe cancel-first confirmation action', () => {
    const fixture = TestBed.createComponent(DialogJourney);
    fixture.detectChanges();
    fixture.componentInstance.$open.set(true);
    fixture.detectChanges();
    fixture.componentInstance.$confirm.set(true);
    fixture.detectChanges();
    const dialogs: NodeListOf<HTMLDialogElement> = fixture.nativeElement.querySelectorAll('dialog');
    expect(dialogs.length).toBe(2);
    expect(dialogs[1].querySelector('button')!.textContent!.trim()).toBe('Cancel');
    dialogs[1].dispatchEvent(new Event('cancel', {cancelable: true}));
    fixture.detectChanges();
    expect(document.body.style.overflow).toBe('hidden');
    expect(fixture.nativeElement.querySelector('dialog').contains(document.activeElement)).toBeTrue();
    fixture.componentInstance.$open.set(false);
    fixture.detectChanges();
    expect(document.body.style.overflow).toBe('');
    fixture.destroy();
  });
});
