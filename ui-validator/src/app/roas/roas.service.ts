import {Injectable} from "@angular/core";

import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';
import {IRoasResponse} from './roas-response';

@Injectable()
export class RoasService {

  private _roasUrl = 'roas/?startFrom=0&pageSize=20';

  constructor(private _http: HttpClient) {
  }

  getRoas(): Observable<IRoasResponse> {
    return this._http.get<IRoasResponse>(this._roasUrl)
      .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
      .catch(this.handleError);
  }

  private handleError(err: HttpErrorResponse) {
    console.log(err.message);
    return Observable.throw(err.message);
  }
}
