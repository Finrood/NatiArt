import {Component} from '@angular/core';
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
export class AlertMessageComponent {
  alertMessages: AlertMessage[] = [];

  /**
   * Display a new alert message and auto-dismiss it after a given timeout (default 3000ms).
   */
  showAlert(alert: AlertMessage, timeout = 3000) {
    // Add the new alert at the beginning so it appears on top.
    this.alertMessages.unshift(alert);
    // Remove the alert after the specified timeout.
    setTimeout(() => this.dismissAlert(alert), timeout);
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
}
