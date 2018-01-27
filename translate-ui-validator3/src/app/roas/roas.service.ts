import {Injectable} from "@angular/core";

import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Observable} from "rxjs/Observable";
import "rxjs/add/operator/do";
import "rxjs/add/operator/catch";
import "rxjs/add/observable/throw";
import {IRoa} from "./roa";
import {IRoasRespons} from "./roas-respons";

@Injectable()
export class RoasService {

    private _roasUrl = './api/roas/roas-response.json';

    constructor(private _http: HttpClient) {
    }

    getRoas(): Observable<IRoasRespons> {
        return this._http.get<IRoasRespons>(this._roasUrl)
            .do(data => console.log('All: ' + JSON.stringify(data.aaData)))
            .catch(this.handleError);
    }
    // getRoas(): Observable<IRoa[]> {
    //     return this._http.get<IRoa[]>(this._roasUrl)
    //         .do(data => console.log('All: ' + JSON.stringify(data)))
    //         .catch(this.handleError);
    // }

    private handleError(err: HttpErrorResponse) {
        console.log(err.message);
        return Observable.throw(err.message);
    }
}