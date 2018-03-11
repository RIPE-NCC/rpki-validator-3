import {ErrorHandler, Injectable, Injector, NgZone} from "@angular/core";
import {Router} from "@angular/router";

@Injectable()
export class ApplicationErrorHandlerService extends ErrorHandler {

  constructor(private injector: Injector) {
    super();
  }

  handleError(error: any) {
    super.handleError(error);
    const router = this.injector.get(Router);
    // ROUTER cannot be used from Services, that's why they suggest using NgZone :|
    const zone: NgZone = this.injector.get(NgZone);
    zone.run(() => router.navigate(['error', { error: error.status, errorMsg: error.statusText}] ));
  }
}
