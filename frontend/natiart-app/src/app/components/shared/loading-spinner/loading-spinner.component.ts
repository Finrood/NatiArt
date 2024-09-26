import {Component} from '@angular/core';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [],
  template: `
    <div class="spinner border-t-4 border-primary border-solid rounded-full w-8 h-8 mx-auto animate-spin"></div>
  `,
  styleUrl: './loading-spinner.component.css'
})
export class LoadingSpinnerComponent {

}
