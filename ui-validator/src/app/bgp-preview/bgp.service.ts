import {Injectable} from "@angular/core";
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IBgpResponse} from "./bgp.model";

@Injectable()
export class BgpService {

  // private _bgpUrl = 'api/bgp';
  private _bgpUrl = '../api/bgp.json';

  constructor(private _http: HttpClient) {}

  getBgp(): Observable<IBgpResponse> {
    return this._http.get<IBgpResponse>(this._bgpUrl)
      .catch(error => Observable.throw(error.message)
      );
  }

  // getBgp(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IBgpResponse> {
  //   const params = new HttpParams()
  //     .set('startFrom', startFrom)
  //     .set('pageSize', pageSize)
  //     .set('search', search)
  //     .set('sortBy', sortBy)
  //     .set('sortDirection', sortDirection);
  //
  //   return this._http.get<IBgpResponse>(this._bgpUrl, {params: params})
  // }
}
