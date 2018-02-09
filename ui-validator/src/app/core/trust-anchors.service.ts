import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import {Injectable} from "@angular/core";

@Injectable()
export class TrustAnchorsService {

    //private _trustAnchorsUrl = './api/trust-anchors/trust-anchors.json';
    private _trustAnchorsUrl = 'trust-anchors/statuses';

    constructor(private _http: HttpClient) {
    }

    getTrustAnchors(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    getTrustAnchorsOverview(): Observable<any> {
        return this._http.get<any>(this._trustAnchorsUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    private handleError(err: HttpErrorResponse) {
        console.log(err.message);
        return Observable.throw(err.message);
    }
}
