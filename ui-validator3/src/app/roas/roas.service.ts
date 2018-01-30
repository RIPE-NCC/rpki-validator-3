import {Injectable} from "@angular/core";

import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import "rxjs/add/operator/do";
import "rxjs/add/operator/catch";
import "rxjs/add/observable/throw";
import {IRoasRespons} from "./roas-respons";

@Injectable()
export class RoasService {

    private _roasUrl = './api/roas/roas-response.json';

    constructor(private _http: HttpClient) {
    }

    getRoas(): Observable<IRoasRespons> {
        return this._http.get<IRoasRespons>(this._roasUrl)
            .do(reponse => console.log('All: ' + JSON.stringify(reponse.data)))
            .catch(this.handleError);
    }

    private handleError(err: HttpErrorResponse) {
        console.log(err.message);
        return Observable.throw(err.message);
    }
}