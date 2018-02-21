import {HttpClient, HttpErrorResponse, HttpParams} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import {Injectable} from "@angular/core";

import {IValidationChecksResponse} from "../monitoring-ta/validation-details/validation-detail";
import {ITrustAnchorResponse} from "../trust-anchors/trust-anchor";

@Injectable()
export class TrustAnchorsService {

    private _trustAnchorsUrl = 'api/trust-anchors';
    private _trustAnchorsStatusesUrl = 'api/trust-anchors/statuses';

    private _trustAnchorByIdUrl = 'api/trust-anchors/{id}';
    private _trustAnchorByIdValidationChecksUrl = 'api/trust-anchors/{id}/validation-checks';
    private _repositoriesByTaId = 'api/rpki-repositories/';


    constructor(private _http: HttpClient) {
    }

    getTrustAnchors(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsUrl)
            .catch(this.handleError);
    }

    getTrustAnchorsOverview(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsStatusesUrl)
            .catch(this.handleError);
    }

    getTrustAnchor(id: string): Observable<ITrustAnchorResponse> {
      return this._http.get<ITrustAnchorResponse>(this._trustAnchorByIdUrl.replace('{id}', id))
          .catch(this.handleError);
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
        {params: params})
    }

    getRepositories(trustAnchorId: string): Observable<any> {
      return this._http.get<any>(this._repositoriesByTaId, { params: { "ta" : trustAnchorId } })
        .do(reponse => console.log('Repositories: ' + JSON.stringify(reponse.data)))
        .catch(this.handleError);
    }

    private handleError(err: HttpErrorResponse) {
        return Observable.throw(err.message);
    }
}
