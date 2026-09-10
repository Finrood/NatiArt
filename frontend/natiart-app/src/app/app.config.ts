import {ApplicationConfig, APP_INITIALIZER} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideHttpClient, withInterceptors} from "@angular/common/http";
import {jwtInterceptor} from "./directory/interceptors/jwt-interceptor.service";
import {AuthenticationService} from "./directory/service/authentication.service";
import {filter, firstValueFrom, take} from "rxjs";

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([jwtInterceptor])
    ),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (authService: AuthenticationService) => () => firstValueFrom(
        authService.authResolved$.pipe(
          filter((resolved: boolean) => resolved),
          take(1)
        )
      ),
      deps: [AuthenticationService]
    }
  ]
};
