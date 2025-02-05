import {Component, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {AuthenticationService} from "../../../service/authentication.service";

@Component({
    selector: 'app-logout',
    imports: [],
    templateUrl: './logout.component.html',
    styleUrl: './logout.component.css'
})
export class LogoutComponent implements OnInit {
  constructor(private router: Router, private authenticationService: AuthenticationService) {
  }

  ngOnInit(): void {
    this.authenticationService.logout();
    this.authenticationService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login'])
      },
      error: (error) => {
        console.error('Logout error', error);
        this.router.navigate(['/login'])
      }
    });
  }
}
