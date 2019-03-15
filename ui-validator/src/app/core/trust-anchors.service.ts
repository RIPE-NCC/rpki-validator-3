import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import {Injectable} from '@angular/core';

import {IValidationChecksResponse} from '../monitoring-ta/validation-details/validation-detail.model';
import {IResponse} from '../shared/response.model';
import {IRepository} from "../monitoring-ta/repositories/repositories.model";

@Injectable()
export class TrustAnchorsService {

    private _trustAnchorsUrl = 'api/trust-anchors';
    private _trustAnchorsStatusesUrl = 'api/trust-anchors/statuses';

    private _trustAnchorByIdUrl = 'api/trust-anchors/{id}';
    private _trustAnchorByIdValidationChecksUrl = 'api/trust-anchors/{id}/validation-checks';
    private _repositoriesByTaId = 'api/rpki-repositories/';
    private _repositoriesById = 'api/rpki-repositories/{id}';
    private _repositoriesStatusesByTaId = 'api/rpki-repositories/statuses/{taId}';


    constructor(private _http: HttpClient) {
    }

    getTrustAnchors(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsUrl);
    }

    getTrustAnchorsOverview(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsStatusesUrl);
    }

    getTrustAnchor(id: string): Observable<IResponse> {
      return this._http.get<IResponse>(this._trustAnchorByIdUrl.replace('{id}', id));
    }

    getTrustAnchorValidationChecks(id: string,
                                   startFrom: string,
                                   pageSize: string,
                                   search: string,
                                   sortBy: string,
                                   sortDirection: string): Observable<IValidationChecksResponse> {
      const params = new HttpParams()
        .set('id', id)
        .set('startFrom', startFrom)
        .set('pageSize', pageSize)
        .set('search', search)
        .set('sortBy', sortBy)
        .set('sortDirection', sortDirection);

      return this._http.get<any>(this._trustAnchorByIdValidationChecksUrl.replace('{id}', id),
        {params: params});
    }

    getRepositories(trustAnchorId: string,
                    startFrom: string,
                    pageSize: string,
                    search: string,
                    sortBy: string,
                    sortDirection: string): Observable<any> {

      const params = new HttpParams()
        .set('ta', trustAnchorId)
        .set('startFrom', startFrom)
        .set('pageSize', pageSize)
        .set('search', search)
        .set('sortBy', sortBy)
        .set('sortDirection', sortDirection);

      return this._http.get<any>(this._repositoriesByTaId, { params: params });
    }

    deleteRepository(repo : IRepository) : Observable<any> {
      return this._http.delete<IResponse>(this._repositoriesById.replace('{id}', repo.id));
    }

    getRepositoriesStatuses(trustAnchorId: string) {
      return this._http.get<any>(this._repositoriesStatusesByTaId.replace('{taId}', trustAnchorId));
    }
}
