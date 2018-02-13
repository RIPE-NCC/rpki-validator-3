import {Injectable} from "@angular/core";
import {HttpClient, HttpErrorResponse, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IRoasResponse} from './roas';

@Injectable()
export class RoasService {

  private _roasUrl = 'api/roas/';

  constructor(private _http: HttpClient) {}

  getRoas(startFrom: string, pageSize: string, search: string): Observable<IRoasResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search);

    return this._http.get<IRoasResponse>(this._roasUrl, {params: params})
      .do(reponse => console.log('All: ' + JSON.stringify(reponse)))
      .catch(this.handleError);
  }

  private handleError(err: HttpErrorResponse) {
    console.log(err.message);
    return Observable.throw(err.message);
  }
}
