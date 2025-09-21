import {ApplicationConfig, APP_INITIALIZER, inject} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideAnimations} from "@angular/platform-browser/animations";
import {provideHttpClient, withInterceptors} from "@angular/common/http";
import {jwtInterceptor} from "./directory/interceptors/jwt-interceptor.service";
import {AuthenticationService} from "./directory/service/authentication.service";

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(
      withInterceptors([jwtInterceptor])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: (authService: AuthenticationService) => () => authService.initializeAuthState(),
      deps: [AuthenticationService],
      multi: true
    }
  ]
};
