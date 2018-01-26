import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-trust-anchors',
  templateUrl: './trust-anchors.component.html',
  styleUrls: ['./trust-anchors.component.css']
})
export class TrustAnchorsComponent implements OnInit {

  constructor(private _route: ActivatedRoute,
              private _router: Router) { }

  ngOnInit() {
  }

}
