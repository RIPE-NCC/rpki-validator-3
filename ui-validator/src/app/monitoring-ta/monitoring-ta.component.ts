import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";

import {TrustAnchorsService} from "../core/trust-anchors.service";
import {IRepository, ITrustAnchor} from "../trust-anchors/trust-anchor";

@Component({
  selector: 'app-monitoring-ta',
  templateUrl: './monitoring-ta.component.html',
  styleUrls: ['./monitoring-ta.component.scss']
})
export class MonitoringTaComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_MONITORING_TA';
  taId: string;
  monitoringTrustAnchor: ITrustAnchor;
  trustAnchorRepositories: IRepository[];
  errorMessage: string;

  constructor(private _activatedRoute: ActivatedRoute, private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.taId = this._activatedRoute.snapshot.url[2].path;
    if (this.taId) {
      this._trustAnchorsService.getTrustAnchor(this.taId)
        .subscribe(response => this.monitoringTrustAnchor = response.data,
            error => this.errorMessage = <any>error

      this._trustAnchorsService.getRepositories(this.taId)
        .subscribe(
          response => this.trustAnchorRepositories = response.data,
          error => this.errorMessage = <any>error
        );
    }
  }
}
