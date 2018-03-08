import {ErrorHandler, Injectable, Injector} from "@angular/core";
import {Router} from "@angular/router";

@Injectable()
export class ApplicationErrorHandlerService extends ErrorHandler {

  constructor(private injector: Injector) {
    super();
  }

  handleError(error) {
    super.handleError(error);
    const router = this.injector.get(Router);
    router.navigate(['error', {error: error.status, errorMsg: error.statusText}] );
  }
}
