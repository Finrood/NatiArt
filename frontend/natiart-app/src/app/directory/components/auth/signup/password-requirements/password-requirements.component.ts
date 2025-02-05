// password-requirements.component.ts
import {Component, Input} from '@angular/core';
import {checkPasswordRequirements, DEFAULT_REQUIREMENTS, PasswordRequirements} from "../../../../utils/password-utils";
import {NgForOf} from "@angular/common";

@Component({
  selector: 'app-password-requirements',
  imports: [
    NgForOf
  ],
  template: `
    <div class="mt-4 space-y-2" aria-live="polite">
      <div *ngFor="let req of requirementsList" class="flex items-center gap-2">
        <svg class="w-4 h-4 shrink-0" [class.text-green-500]="req.valid" [class.text-gray-400]="!req.valid" fill="none"
             viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
        </svg>
        <span class="text-sm" [class.text-gray-600]="req.valid" [class.text-gray-400]="!req.valid">{{ req.text }}</span>
        <div class="relative flex-1">
          <div class="absolute inset-0 h-0.5 bg-gray-200"></div>
          <div class="absolute inset-0 h-0.5 transition-all duration-500"
               [class.bg-green-500]="req.valid"
               [class.w-0]="!req.valid"
               [class.w-full]="req.valid"></div>
        </div>
      </div>
    </div>
  `
})
export class PasswordRequirementsComponent {
  @Input() password = '';
  @Input() requirements: PasswordRequirements = DEFAULT_REQUIREMENTS;

  get requirementsList() {
    const results = checkPasswordRequirements(this.password, this.requirements);
    return [
      {
        text: `Minimum ${this.requirements.minLength} characters`,
        valid: results.hasMinLength
      },
      {
        text: 'Contains lowercase letter',
        valid: results.hasLower
      },
      {
        text: 'Contains uppercase letter',
        valid: results.hasUpper
      },
      {
        text: 'Contains number',
        valid: results.hasNumber
      }
    ];
  }
}
