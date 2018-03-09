import {Component, Input, OnChanges, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";

import {BgpService} from "../core/bgp.service";
import {BgpDataStore} from "../core/bgp-data.store";
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
  @Input()
  prefix: string;
  @Input()
  asn: string;
  @ViewChild('modalEnterAsnPrefix')
  private modalTempRef : TemplateRef<any>

  constructor(private _activatedRoute: ActivatedRoute,
              private _router: Router,
              private _bgpService: BgpService,
              private _bgpDataService: BgpDataStore,
              private modalService: NgbModal) {
  }

  ngOnInit() {
    this.loadAnnouncementPreview();
  }

  loadAnnouncementPreview():void {
    this.prefix = this._activatedRoute.snapshot.url[1].path;
    if (this._bgpDataService.bgpData) {
      // navigated from bgp-preview component
      this.selectedBgp = this._bgpDataService.bgpData;
    } else if (this.prefix === ':prefix' || this.asn === ':asn') {
      // open modal dialog... or show warning page
      this.prefix = this.prefix === ':prefix' ? '' : this.prefix;
      this.asn = this.asn === ':asn' ? '' : this.asn;
      this.openModalForm(this.modalTempRef);
    } else {
      // bookmarked url and prefix coming from url
      this.selectedBgp.prefix = this.prefix;
    }
    this.getAnnouncementPreviewData();
  }

  getAnnouncementPreviewData(): void {
    this._bgpService.getBgpAnnouncementPreview(this.selectedBgp.prefix)
      .subscribe(
        response => this.announcementPreviewData = response.data
      )
  }

  openModalForm(modalTempRef): void {
    this.modalService.open(modalTempRef).result.then((result) => {
      console.log(result);
      this._router.navigate(['/announcement-preview/', this.prefix]);
    }, (reason) => {
    });
  }
}
