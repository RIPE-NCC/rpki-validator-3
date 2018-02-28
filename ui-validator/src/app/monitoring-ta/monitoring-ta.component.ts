import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";

import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchor} from "../trust-anchors/trust-anchor.model";
import {IRepositoriesStatuses} from "./repositories/repositories.model";

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

  constructor(private _activatedRoute: ActivatedRoute,
              private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.taId = this._activatedRoute.snapshot.url[2].path;
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
