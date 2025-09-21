import {Component, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {AuthenticationService} from "../../../service/authentication.service";
import {CommonModule} from "@angular/common";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";

@Component({
    selector: 'app-logout',
    imports: [CommonModule, LoadingSpinnerComponent],
    templateUrl: './logout.component.html',
    styleUrl: './logout.component.css',
    standalone: true
})
export class LogoutComponent implements OnInit {
  loggedOut = false;

  constructor(private router: Router, private authenticationService: AuthenticationService) {
  }

  ngOnInit(): void {
    this.authenticationService.logout().subscribe({
      next: () => {
        this.loggedOut = true;
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
      },
      error: (error) => {
        console.error('Logout error', error);
        // Even if there's an error, we should probably still redirect to login
        this.router.navigate(['/login']);
      }
    });
  }
}
