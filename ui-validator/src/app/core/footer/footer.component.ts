import {Component, OnInit} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {IResponse} from "../../shared/response.model";

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent implements OnInit {

  version: string;

  constructor(private _http: HttpClient) {}

  ngOnInit() {
    this._http.get<IResponse>('/api/healthcheck', {})
      .subscribe(
        response => {
          this.version = response.data.buildInformation.version
        });
  }

}
