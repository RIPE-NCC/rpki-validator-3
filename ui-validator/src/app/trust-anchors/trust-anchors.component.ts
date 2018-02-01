import { Component, OnInit } from '@angular/core';
import {TrustAnchorsService} from './trust-anchors.service';
import {ITrustAnchor} from './trust-anchor';

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.scss']
})
export class TrustAnchorsComponent implements OnInit {

  trustAnchors: ITrustAnchor[] = [];
  errorMessage: string;

  constructor(private _trustAnchorsService: TrustAnchorsService) { }

  ngOnInit() {
      this._trustAnchorsService.getTrustAnchors()
          .subscribe(response => this.trustAnchors = response.data,
              error => this.errorMessage = <any>error);
  }
}
