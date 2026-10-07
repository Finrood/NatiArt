import {Component, Input} from '@angular/core';
import {checkPasswordRequirements, DEFAULT_REQUIREMENTS, PasswordRequirements} from "../../../../utils/password-utils";


@Component({
  selector: 'app-password-requirements',
  imports: [],
  template: `
    <ul class="mt-3 space-y-2 text-sm text-secondary" aria-label="Password requirements" i18n-aria-label aria-live="polite">
      @for (req of requirementsList; track req.text) {
        <li class="flex items-center gap-2">
          @if (req.valid) {
            <svg aria-hidden="true" class="w-4 h-4 shrink-0 text-green-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
            <span class="sr-only" i18n>Met:</span>
          } @else {
            <span aria-hidden="true" class="w-4 text-center">•</span>
            <span class="sr-only" i18n>Needed:</span>
          }
          <span>{{ req.text }}</span>
        </li>
      }
    </ul>
  `
})
export class PasswordRequirementsComponent {
  @Input() password = '';
  @Input() requirements: PasswordRequirements = DEFAULT_REQUIREMENTS;

  get requirementsList(): Array<{text: string; valid: boolean}> {
    const results = checkPasswordRequirements(this.password, this.requirements);
    const entered: boolean = typeof this.password === 'string' && this.password.length > 0;
    return [
      {
        text: $localize`Minimum ${this.requirements.minLength}:MIN_LENGTH: characters`,
        valid: entered && results.hasMinLength
      },
      {
        text: $localize`Contains lowercase letter`,
        valid: entered && results.hasLower
      },
      {
        text: $localize`Contains uppercase letter`,
        valid: entered && results.hasUpper
      },
      {
        text: $localize`Contains number`,
        valid: entered && results.hasNumber
      }
    ];
  }
}
