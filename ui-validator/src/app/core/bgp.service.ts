import {Injectable} from "@angular/core";
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';

import {IBgpResponse} from "../bgp-preview/bgp.model";
import {IAnnouncementResponse} from "../announcement-preview/announcement.model";

@Injectable()
export class BgpService {

  private _bgpUrl = 'api/bgp/';
  private _bgpValidityUrl = 'api/bgp/validity';

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

  getBgpAnnouncementPreview(prefix: string): Observable<IAnnouncementResponse> {
    const params = new HttpParams()
      .set('prefix', prefix);

    return this._http.get<IAnnouncementResponse>(this._bgpValidityUrl, {params: params});
  }
}
