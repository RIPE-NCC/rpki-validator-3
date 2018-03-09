import {Component, OnInit} from '@angular/core';
import {Router} from "@angular/router";

import {ITrustAnchorOverview} from "./trust-anchor.model";
import {TrustAnchorsService} from "../core/trust-anchors.service";
import {MonitoringTaDataStore} from "../core/monitoring-ta-data.store";

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.scss']
})
export class TrustAnchorsComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_TRUST_ANCHORS';
  trustAnchorsOverview: ITrustAnchorOverview[] = [];
  errorMessage: string;

  constructor(private _trustAnchorsService: TrustAnchorsService,
              private _monitoringTaDataServices: MonitoringTaDataStore,
              private _router: Router) {
  }

  ngOnInit() {
    this._trustAnchorsService.getTrustAnchorsOverview()
      .subscribe(
        response => this.trustAnchorsOverview = response.data);
  }

  openTADetails(selectedTA: ITrustAnchorOverview) {
    this._monitoringTaDataServices.selectedTA = selectedTA;
    this._router.navigate(['/trust-anchors/monitor', selectedTA.id]);
  }
}
