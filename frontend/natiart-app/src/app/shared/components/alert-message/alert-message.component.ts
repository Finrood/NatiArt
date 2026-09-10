import {Component, OnDestroy} from '@angular/core';
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
  alertMessages: AlertMessage[] = [];
  private readonly dismissTimers = new Set<ReturnType<typeof setTimeout>>();

  /**
   * Display a new alert message and auto-dismiss it after a given timeout (default 3000ms).
   */
  showAlert(alert: AlertMessage, timeout = 3000) {
    // Add the new alert at the beginning so it appears on top.
    this.alertMessages.unshift(alert);
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
  dismissAlert(alert: AlertMessage) {
    const index = this.alertMessages.indexOf(alert);
    if (index > -1) {
      this.alertMessages.splice(index, 1);
    }
  }

  ngOnDestroy(): void {
    for (const dismissTimer of this.dismissTimers) {
      clearTimeout(dismissTimer);
    }
    this.dismissTimers.clear();
  }
}
