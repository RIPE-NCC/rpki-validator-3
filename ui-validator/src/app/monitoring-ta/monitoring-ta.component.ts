import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";

import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchor, ITrustAnchorOverview} from "../trust-anchors/trust-anchor.model";
import {IRepositoriesStatuses} from "./repositories/repositories.model";
import {MonitoringTaDataServices} from "../core/monitoring-ta-data.services";

@Component({
  selector: 'app-monitoring-ta',
  templateUrl: './monitoring-ta.component.html',
  styleUrls: ['./monitoring-ta.component.scss']
})
export class MonitoringTaComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_MONITORING_TA';
  taId: string;
  monitoringTrustAnchor: ITrustAnchor;
  repositoriesStatuses: IRepositoriesStatuses;
  tAOverview: ITrustAnchorOverview;

  constructor(private _activatedRoute: ActivatedRoute,
              private _trustAnchorsService: TrustAnchorsService,
              private _monitoringTaDataServices: MonitoringTaDataServices) {
  }

  ngOnInit() {
    this.taId = this._activatedRoute.snapshot.url[2].path;
    this.tAOverview = this._monitoringTaDataServices.selectedTA;
    if (this.taId) {
      this.getTrustAnchors();
    }
  }

  getTrustAnchors() {
    this._trustAnchorsService.getTrustAnchor(this.taId)
      .subscribe(
        response => this.monitoringTrustAnchor = response.data
      )
  }

  getRepositoriesStatuses() {
    this._trustAnchorsService.getRepositoriesStatuses(this.taId)
      .subscribe(
        response => this.repositoriesStatuses = response.data
      )
  }
}
