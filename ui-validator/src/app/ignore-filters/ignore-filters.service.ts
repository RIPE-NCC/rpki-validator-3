import {Injectable} from "@angular/core";
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IIgnoreFilter, IIgnoreFiltersResponse} from "./filters.model";

@Injectable()
export class IgnoreFiltersService {

  private _ignoreFiltersUrl = '../api/ignore-filters-response.json';

  constructor(private _http: HttpClient) {}

  getIgnoreFilters(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IIgnoreFiltersResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IIgnoreFiltersResponse>(this._ignoreFiltersUrl, {params: params})
  }
  //TODO make it really work
  saveIgnoreFilter(filter: IIgnoreFilter): Observable<IIgnoreFiltersResponse> {
    return this._http.get<IIgnoreFiltersResponse>(this._ignoreFiltersUrl)
  }
  //TODO make it really work
  deleteIgnoreFilter(filter: IIgnoreFilter): Observable<IIgnoreFiltersResponse> {
    return this._http.get<IIgnoreFiltersResponse>(this._ignoreFiltersUrl)
  }
}
