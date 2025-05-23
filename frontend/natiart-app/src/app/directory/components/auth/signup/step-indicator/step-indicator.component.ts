import {Component, Input, OnInit} from '@angular/core';
import {NgClass, NgForOf} from "@angular/common";

@Component({
  selector: 'app-step-indicator',
  imports: [
    NgForOf,
    NgClass
  ],
  templateUrl: './step-indicator.component.html',
  styleUrl: './step-indicator.component.css'
})
export class StepIndicatorComponent implements OnInit {
  @Input() currentStep!: number;

  ngOnInit() {
    console.log(this.currentStep);
  }
}
