import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IResponse} from "../shared/response.model";

export interface IIgnoreFilter {
  id: string,
  prefix: string,
  asn: string,
  comment: string
}

@Injectable()
export class IgnoreFiltersService {

  private _ignoreFiltersUrl = '/api/ignore-filters';
  private _deleteIgnoreFilterUrl = '/api/ignore-filters/{id}';

  constructor(private _http: HttpClient) {}

  getIgnoreFilters(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IResponse>(this._ignoreFiltersUrl, {params: params})
  }

  saveIgnoreFilter(filter: IIgnoreFilter): Observable<any> {
    return this._http.post(this._ignoreFiltersUrl, { data: filter });
  }

  deleteIgnoreFilter(filter: IIgnoreFilter): Observable<any> {
    return this._http.delete<IResponse>(this._deleteIgnoreFilterUrl.replace('{id}', filter.id))
  }
}
