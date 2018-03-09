import {Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {NgbModal, NgbModalRef} from "@ng-bootstrap/ng-bootstrap";

import {BgpService} from "../core/bgp.service"
import {IAnnouncement, IAnnouncementData, IAnnouncementResponse} from "./announcement.model";

@Component({
  selector: 'app-announcement-preview',
  templateUrl: './announcement-preview.component.html',
  styleUrls: ['./announcement-preview.component.scss']
})
export class AnnouncementPreviewComponent implements OnInit {
  pageTitle: string = 'Nav.TITLE_ANNOUNCEMENT_PREVIEW';
  announcementPreviewData: IAnnouncementData;

  @Input()
  prefix: string;
  @Input()
  asn: string;
  @ViewChild('modalEnterAsnPrefix')
  private modalTempRef : TemplateRef;
  private modalRef: NgbModalRef;

  constructor(private _activatedRoute: ActivatedRoute,
              private _router: Router,
              private _bgpService: BgpService,
              private _modalService: NgbModal) {
  }

  ngOnInit() {
    this.loadAnnouncementPreview();
  }

  loadAnnouncementPreview():void {
    this.asn = this._activatedRoute.snapshot.url[1].path;
    this.prefix = this._activatedRoute.snapshot.url[2].path;
    if (this.prefix && this.prefix !== ':prefix'
      && this.asn && this.asn !== ':asn') {
      this.getAnnouncementPreviewData();
    } else {
      // open modal dialog
      this.prefix = this.prefix === ':prefix' ? '' : this.prefix;
      this.asn = this.asn === ':asn' ? '' : this.asn;
      this.openModalForm(this.modalTempRef);
    }
  }

  getAnnouncementPreviewData(): void {
    this._bgpService.getBgpAnnouncementPreview(this.prefix, this.asn)
      .subscribe(
        response => this.announcementPreviewData = response.data
      )
  }

  //TODO NEED TO BE CHANGED BEHAVIOR
  openModalForm(modalTempRef): void {
    this.modalRef = this._modalService.open(modalTempRef);
    this.modalRef.result.then(result => {
      this._router.navigate(['/announcement-preview/', this.asn, this.prefix]);
      this.modalRef.close();
    }, dismiss => {
      this._router.navigate(['/bgp-preview']);
      this.modalRef.close();
    });
  }
}
