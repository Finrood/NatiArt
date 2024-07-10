import {Injectable} from '@angular/core';
import {HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from "@angular/common/http";
import {catchError, Observable, throwError} from "rxjs";
import {Router} from "@angular/router";
import {TokenService} from "../service/token.service";

@Injectable({
  providedIn: 'root'
})
export class JwtInterceptorService implements HttpInterceptor {
  private tokenExpiryCheckIntervalId: any;

  constructor(private router: Router, private tokenService: TokenService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (this.tokenService.isAccessTokenExpired()) {
      return this.refreshTokenAndRetry(req, next);
    }

    const accessToken = this.tokenService.getAccessToken();
    const cloned = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${accessToken}`)
    });

    return next.handle(cloned).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          // Redirect to logout if accessToken is invalid/expired
          this.router.navigate(['/logout'])
            .then(() => {});
        }
        return throwError(() => error);
      })
    );
  }

  private refreshTokenAndRetry(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    this.tokenService.refreshToken();

    const accessToken = this.tokenService.getAccessToken();
    const reqWithNewHeader = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${accessToken}`)
    });
    return this.intercept(reqWithNewHeader, next)
  }

  public startTokenExpiryCheck(): void {
    if (!this.tokenExpiryCheckIntervalId) {
      this.tokenExpiryCheckIntervalId = setInterval(() => {
        if (this.tokenService.getAccessToken() && this.tokenService.isAccessTokenExpired()) {
          console.log("Tokens have expired. Trying to renew them");
          console.log(this.tokenExpiryCheckIntervalId)
          this.tokenService.refreshToken();
        }
      }, 10000); // Every second
    }
  }
}
