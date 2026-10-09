import {Component, DestroyRef, inject, signal, OnDestroy, OnInit, WritableSignal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Router} from "@angular/router";
import {AuthenticationService} from "../../../service/authentication.service";
import {CommonModule} from "@angular/common";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
    selector: 'app-logout',
    imports: [CommonModule, LoadingSpinnerComponent],
    templateUrl: './logout.component.html',
    styleUrl: './logout.component.css'
})
export class LogoutComponent implements OnInit, OnDestroy {
  readonly $loggedOut: WritableSignal<boolean> = signal(false);
  get loggedOut(): boolean { return this.$loggedOut(); }
  private readonly _router: Router = inject(Router);
  private readonly _authenticationService: AuthenticationService = inject(AuthenticationService);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);

  private redirectTimer: ReturnType<typeof setTimeout> | undefined = undefined;

  ngOnInit(): void {
    this._authenticationService.logout().pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: () => {
        this.$loggedOut.set(true);
        this.redirectTimer = setTimeout(() => {
          this._router.navigate(['/login']);
        }, 2000);
      },
      error: (error) => {
        reportError('logout', error);
        this._router.navigate(['/login']);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.redirectTimer !== undefined) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = undefined;
    }
  }
}
