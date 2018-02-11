import {Component, OnInit} from '@angular/core';

import {Router} from "@angular/router";
import {ITrustAnchorOverview} from "./trust-anchor";
import {TrustAnchorsService} from "../core/trust-anchors.service";

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.scss']
})
export class TrustAnchorsComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_TRUST_ANCHORS';
  trustAnchorsOverview: ITrustAnchorOverview[] = [];
  errorMessage: string;

  constructor(private _trustAnchorsService: TrustAnchorsService, private _router: Router) {
  }

  ngOnInit() {
    this._trustAnchorsService.getTrustAnchorsOverview()
      .subscribe(response => {
            this.trustAnchorsOverview = response.data
          },
          error => this.errorMessage = <any>error);
  }

  openTADetails(taId: string) {
    console.log("row clicked "+taId);
    this._router.navigate(['/trust-anchors/monitor', taId]);
  }
}
