import {Component, OnInit} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Router} from "@angular/router";
import {TokenService} from "../../../service/token.service";

@Component({
  selector: 'app-logout',
  standalone: true,
  imports: [],
  templateUrl: './logout.component.html',
  styleUrl: './logout.component.css'
})
export class LogoutComponent implements OnInit {
  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {}

  ngOnInit(): void {
    this.tokenService.clearTokens();
    this.tokenService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login'])
          .then(() => {});
      },
      error: (error) => {
        console.error('Logout error', error);
        this.router.navigate(['/login'])
          .then(() => {});
      }
    });
  }
}
