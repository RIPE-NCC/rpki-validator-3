import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import {Injectable} from "@angular/core";
import {ITrustAnchorsRespons} from "./trust-anchors-respons";

@Injectable()
export class TrustAnchorsService {

    private _trustAnchorsUrl = './api/trust-anchors/trust-anchors.json';

    constructor(private _http: HttpClient) {
    }

    getTrustAnchors(): Observable<ITrustAnchorsRespons> {
        return this._http.get<ITrustAnchorsRespons>(this._trustAnchorsUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    private handleError(err: HttpErrorResponse) {
        console.log(err.message);
        return Observable.throw(err.message);
    }
}