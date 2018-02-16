import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";

import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchor} from "../trust-anchors/trust-anchor";

@Component({
  selector: 'app-monitoring-ta',
  templateUrl: './monitoring-ta.component.html',
  styleUrls: ['./monitoring-ta.component.scss']
})
export class MonitoringTaComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_MONITORING_TA';
  monitoringTrustAnchor: ITrustAnchor;
  errorMessage: string;

  constructor(private _activatedRoute: ActivatedRoute, private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    let id = this._activatedRoute.snapshot.url[2].path;
    if (id) {
      this._trustAnchorsService.getTrustAnchor(id)
        .subscribe(response => this.monitoringTrustAnchor = response.data,
          error => this.errorMessage = <any>error);
    }
  }

}
