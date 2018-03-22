import {Injectable} from "@angular/core";
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';

import {IAnnouncementResponse} from "../announcement-preview/announcement.model";
import {IResponse} from "../shared/response.model";

@Injectable()
export class BgpService {

  private _bgpUrl = 'api/bgp/';
  private _bgpValidityUrl = 'api/bgp/validity';

  constructor(private _http: HttpClient) {}

  getBgp(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IResponse>(this._bgpUrl, {params: params});
  }

  getBgpAnnouncementPreview(prefix: string, asn: string): Observable<IAnnouncementResponse> {
    const params = new HttpParams()
      .set('prefix', prefix)
      .set('asn', asn);

    return this._http.get<IAnnouncementResponse>(this._bgpValidityUrl, {params: params});
  }
}
