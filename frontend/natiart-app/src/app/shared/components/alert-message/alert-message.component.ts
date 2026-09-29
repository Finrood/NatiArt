import {Component, OnDestroy, signal} from '@angular/core';
import { NgClass } from '@angular/common';

export interface AlertMessage {
  type: 'success' | 'error';
  message: string;
}

@Component({
  selector: 'app-alert-messages',
  templateUrl: './alert-message.component.html',
  imports: [NgClass],
})
export class AlertMessageComponent implements OnDestroy {
  readonly $alertMessages = signal<AlertMessage[]>([]);
  get alertMessages(): AlertMessage[] { return this.$alertMessages(); }
  private readonly dismissTimers = new Set<ReturnType<typeof setTimeout>>();

  /**
   * Display a new alert message and auto-dismiss it after a given timeout (default 3000ms).
   */
  showAlert(alert: AlertMessage, timeout: number = 3000): void {
    // Add the new alert at the beginning so it appears on top.
    this.$alertMessages.update((messages: AlertMessage[]): AlertMessage[] => [alert, ...messages]);
    // Remove the alert after the specified timeout.
    const dismissTimer: ReturnType<typeof setTimeout> = setTimeout(() => {
      this.dismissTimers.delete(dismissTimer);
      this.dismissAlert(alert);
    }, timeout);
    this.dismissTimers.add(dismissTimer);
  }

  /**
   * Remove a given alert from the list.
   */
  dismissAlert(alert: AlertMessage): void {
    this.$alertMessages.update((messages: AlertMessage[]): AlertMessage[] => messages.filter((message: AlertMessage) => message !== alert));
  }

  ngOnDestroy(): void {
    for (const dismissTimer of this.dismissTimers) {
      clearTimeout(dismissTimer);
    }
    this.dismissTimers.clear();
  }
}
