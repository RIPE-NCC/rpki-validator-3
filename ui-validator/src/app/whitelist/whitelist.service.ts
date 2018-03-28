import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import 'rxjs/add/operator/catch';
import 'rxjs/add/observable/throw';

import {IResponse} from "../shared/response.model";

export interface IWhitelistEntry {
  id: string,
  prefix?: string,
  asn?: string,
  comment?: string,
  maximumLength: number
}

@Injectable()
export class WhitelistService {

  private _whitelistUrl = '/api/roa-prefix-assertions';
  private _deleteWhitelistEntryUrl = '/api/roa-prefix-assertions/{id}';
  private _slurmUploadUrl = 'api/slurm/upload';

  constructor(private _http: HttpClient) {}

  getWhitelist(startFrom: string, pageSize: string, search: string, sortBy: string, sortDirection: string): Observable<IResponse> {
    const params = new HttpParams()
      .set('startFrom', startFrom)
      .set('pageSize', pageSize)
      .set('search', search)
      .set('sortBy', sortBy)
      .set('sortDirection', sortDirection);

    return this._http.get<IResponse>(this._whitelistUrl, {params: params})
  }

  saveWhitelistEntry(entry: IWhitelistEntry): Observable<any> {
    return this._http.post(this._whitelistUrl, { data: entry });
  }

  uploadSlurm(formModel: IWhitelistEntry): Observable<any> {
    return this._http.post(this._slurmUploadUrl, formModel);
  }

  deleteWhitelistEntry(entry: IWhitelistEntry): Observable<any> {
    return this._http.delete<IResponse>(this._deleteWhitelistEntryUrl.replace('{id}', entry.id))
  }
}
