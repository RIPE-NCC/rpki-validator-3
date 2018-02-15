import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import {Injectable} from "@angular/core";

@Injectable()
export class TrustAnchorsService {

    private _trustAnchorsUrl = 'api/trust-anchors';
    private _trustAnchorsStatusesUrl = 'api/trust-anchors/statuses';
    private _trustAnchorByIdUrl = 'api/trust-anchors/';

    constructor(private _http: HttpClient) {
    }

    getTrustAnchors(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    getTrustAnchorsOverview(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsStatusesUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    getTrustAnchor(id: string): Observable<any> {
      return this._http.get<any>(this._trustAnchorByIdUrl + id)
          .do(reponse => console.log('ID RESPONSE: ' + JSON.stringify(reponse.data)))
          .catch(this.handleError);
    }

    private handleError(err: HttpErrorResponse) {
        console.log(err.message);
        return Observable.throw(err.message);
    }
}
