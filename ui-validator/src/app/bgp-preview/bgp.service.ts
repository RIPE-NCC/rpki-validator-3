import {Injectable} from "@angular/core";
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IBgpResponse} from "./bgp.model";

@Injectable()
export class BgpService {

  private _bgpUrl = 'api/bgp/';

  constructor(private _http: HttpClient) {}

  getBgp(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IBgpResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IBgpResponse>(this._bgpUrl, {params: params});
  }
}
