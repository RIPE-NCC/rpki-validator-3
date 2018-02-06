import {Component, OnInit} from '@angular/core';
import {TrustAnchorsService} from './trust-anchors.service';
import {ITrustAnchor} from './trust-anchor';
import {Router} from "@angular/router";

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.scss']
})
export class TrustAnchorsComponent implements OnInit {

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
    this.router.navigate(['/trust-anchor-monitor', taId]);
  }
}
