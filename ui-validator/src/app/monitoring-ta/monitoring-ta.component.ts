import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

import {TrustAnchorsService} from '../core/trust-anchors.service';
import {ITrustAnchor, ITrustAnchorOverview} from '../trust-anchors/trust-anchor.model';
import {IRepositoriesStatuses} from './repositories/repositories.model';
import {MonitoringTaDataStore} from '../core/monitoring-ta-data.store';

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
              private _monitoringTaDataStore: MonitoringTaDataStore) {
  }

  ngOnInit() {
    this.taId = this._activatedRoute.snapshot.url[2].path;
    if (this.taId) {
      this.getTrustAnchor();
    }
    // in case previously was clicked on TA page to see details
    this.tAOverview = this._monitoringTaDataStore.selectedTA;
    // if url was refreshed
    if (!this.tAOverview) {
      this.getTrustAnchorOverview();
    }
  }

  getTrustAnchor() {
    this._trustAnchorsService.getTrustAnchor(this.taId)
      .subscribe(
        response => this.monitoringTrustAnchor = response.data
      );
  }

  // get TA overview details in case this page wasn't open navigating from TA page
  getTrustAnchorOverview() {
    this._trustAnchorsService.getTrustAnchorsOverview()
      .subscribe(
        response => this.tAOverview = response.data.find(ta => ta.id === this.taId)
        );
  }

  getRepositoriesStatuses() {
    this._trustAnchorsService.getRepositoriesStatuses(this.taId)
      .subscribe(
        response => this.repositoriesStatuses = response.data
      );
  }
}
