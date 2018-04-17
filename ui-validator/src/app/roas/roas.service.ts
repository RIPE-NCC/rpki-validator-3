import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IResponse} from "../shared/response.model";

@Injectable()
export class RoasService {

  private _roasUrl = 'api/roas';

  constructor(private _http: HttpClient) {}

  getRoas(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IResponse>(this._roasUrl, {params: params})
  }
}
