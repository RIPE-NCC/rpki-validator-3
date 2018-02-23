import {ErrorHandler, Injectable, Injector} from "@angular/core";
import {Router} from "@angular/router";

@Injectable()
export class ApplicationErrorHandler implements ErrorHandler {

  constructor(private injector: Injector) {
  }

  handleError(error) {
    const router = this.injector.get(Router);
    router.navigate(['error', {error: error.status, errorMsg: error.statusText}] );
  }
}
