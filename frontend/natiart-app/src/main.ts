import {bootstrapApplication} from '@angular/platform-browser';
import {appConfig} from './app/app.config';
import {AppComponent} from './app/app.component';
import {reportError} from './app/shared/service/error-reporting.service';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => reportError('application-bootstrap', err));
