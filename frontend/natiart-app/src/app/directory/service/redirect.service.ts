import {Injectable} from '@angular/core';
import {Router, UrlTree} from "@angular/router";

@Injectable({
  providedIn: 'root'
})
export class RedirectService {
  private redirectUrl: string | null = null;


  constructor(private router: Router) {
  }

  setRedirectUrl(url: string) {
    this.redirectUrl = url;
  }

  getRedirectUrl(): string | null {
    const url = this.redirectUrl;
    this.redirectUrl = null;
    return url;
  }

  public getLoginTree(): UrlTree {
    return this.router.parseUrl('/login');
  }

  public getDashboardTree(): UrlTree {
    return this.router.parseUrl('/dashboard');
  }
}
