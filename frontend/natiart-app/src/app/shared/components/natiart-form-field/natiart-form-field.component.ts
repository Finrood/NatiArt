import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {AbstractControl, FormGroup} from "@angular/forms";
import {NgIf} from "@angular/common";
import {ButtonComponent} from "../button.component";

@Component({
  selector: 'app-natiart-form-field',
  standalone: true,
  imports: [
    NgIf,
    ButtonComponent
  ],
  templateUrl: './natiart-form-field.component.html',
  styleUrl: './natiart-form-field.component.css'
})
export class NatiartFormFieldComponent implements OnInit {
  @Input() label!: string;
  @Input() control!: AbstractControl | null;
  @Input() controlName!: string;
  @Input() form!: FormGroup;
  @Input() isPassword: boolean = false;

  showPassword = false;
  @Output() showPasswordEmitter = new EventEmitter<void>();
  @Input() isOptional!: boolean;

  ngOnInit() {
    this.control = this.form.get(this.controlName);
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
    this.showPasswordEmitter.emit();
  }

  showErrors(): boolean {
    return !!this.control && this.control.invalid && (this.control.dirty || this.control.touched);
  }
}
