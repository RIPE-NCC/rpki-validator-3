import {ErrorHandler, Injectable, Injector, NgZone} from "@angular/core";
import {Router} from "@angular/router";

@Injectable()
export class ApplicationErrorHandlerService extends ErrorHandler {

  constructor(private injector: Injector) {
    super();
  }

  handleError(error) {
    super.handleError(error);
    const router = this.injector.get(Router);
    const zone = this.injector.get(NgZone);
    zone.run(() => router.navigate(['error', {error: error.status, errorMsg: error.statusText}] ));
  }
}
