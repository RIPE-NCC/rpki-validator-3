import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {BgpService} from "../core/bgp.service";
import {BgpDataService} from "../core/bgp-data.service";
import {IBgp} from "../bgp-preview/bgp.model";
import {IAnnouncement} from "./announcement.model";

@Component({
  selector: 'app-announcement-preview',
  templateUrl: './announcement-preview.component.html',
  styleUrls: ['./announcement-preview.component.scss']
})
export class AnnouncementPreviewComponent implements OnInit {
  pageTitle: string = 'Nav.TITLE_ANNOUNCEMENT_PREVIEW';
  selectedBgp: IBgp = {asn: '', prefix: '', validity: ''};
  announcementPreviewData: Array<IAnnouncement>;
  prefix: string;

  constructor(private _activatedRoute: ActivatedRoute,
              private _bgpService: BgpService,
              private _bgpDataService: BgpDataService) {
  }

  ngOnInit() {
    //TODO if asn and prefix doesn't exist show modal dialog to specified asn and prefix
    let prefix = this._activatedRoute.snapshot.url[1].path;
    if (this._bgpDataService.bgpData) {
      // navigated from bgp-preview component
      this.selectedBgp = this._bgpDataService.bgpData;
    } else if (prefix) {
      // bookmarked url and prefix coming from url
      this.selectedBgp.prefix = prefix;
    } else {
      // open modal dialog... or show warning page
    }
    this.getAnnouncementPreviewData();
  }

  getAnnouncementPreviewData(): void {
    this._bgpService.getBgpAnnouncementPreview(this.selectedBgp.prefix)
      .subscribe(
        response => this.announcementPreviewData = response.data
      )
  }
}
