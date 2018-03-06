import { Component, OnInit } from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {BgpService} from "../bgp-preview/bgp.service";

@Component({
  selector: 'app-announcement-preview',
  templateUrl: './announcement-preview.component.html',
  styleUrls: ['./announcement-preview.component.scss']
})
export class AnnouncementPreviewComponent implements OnInit {
  pageTitle: string = 'Nav.TITLE_ANNOUNCEMENT_PREVIEW';
  asn: string;
  prefix: string;
  //FIXME change any
  announcementPreviewData: any;

  constructor(private _activatedRoute: ActivatedRoute,
              private _bgpService:BgpService) { }

  ngOnInit() {
    //TODO if asn and prefix doesn't exist show modal dialog to specified asn and prefix
    this.asn = this._activatedRoute.snapshot.url[1].path;
    this.prefix = this._activatedRoute.snapshot.url[2].path;
    if (this.asn && this.prefix) {
      this.getAnnouncementPreviewData();
    }
  }

  getAnnouncementPreviewData() {
    this._bgpService.getBgpAnnouncementPreview(this.asn, this.prefix)
      .subscribe(
        response => this.announcementPreviewData = response.data
      )
  }
}
