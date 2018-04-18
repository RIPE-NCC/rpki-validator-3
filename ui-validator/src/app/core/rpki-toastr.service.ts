import {ToastrService} from 'ngx-toastr';
import {TranslateService} from '@ngx-translate/core';
import {Injectable} from '@angular/core';

@Injectable()
export class RpkiToastrService {

  constructor(private _translateService: TranslateService,
              private _toastrService: ToastrService) {
  }

  success(key: string) {
    this._toastrService.success(this._translateService.instant(key));
  }

  error(key: string) {
    this._toastrService.error(this._translateService.instant(key));
  }

  warning(key: string) {
    this._toastrService.warning(this._translateService.instant(key));
  }

  info(key: string) {
    this._toastrService.info(this._translateService.instant(key));
  }
}
