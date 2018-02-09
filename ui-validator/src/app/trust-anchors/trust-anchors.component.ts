import {Component, OnInit} from '@angular/core';

import {Router} from "@angular/router";
import {ITrustAnchor} from "./trust-anchor";
import {TrustAnchorsService} from "../core/trust-anchors.service";

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.scss']
})
export class TrustAnchorsComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_TRUST_ANCHORS';
  trustAnchors: ITrustAnchor[] = [];
  errorMessage: string;

  constructor(private _trustAnchorsService: TrustAnchorsService, private router: Router) {
  }

  ngOnInit() {
    this._trustAnchorsService.getTrustAnchors()
      .subscribe(response => this.trustAnchors = response.data,
        error => this.errorMessage = <any>error);
  }

  openTADetails(taId: string) {
    console.log("row clicked "+taId);
    this.router.navigate(['/trust-anchors/monitor', taId]);
  }
}
