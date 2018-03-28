import {Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {NgbModal, NgbModalRef} from "@ng-bootstrap/ng-bootstrap";

import {BgpService} from "../core/bgp.service"
import {IAnnouncementData} from "./announcement.model";

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
  private modalTempRef : TemplateRef<any>;
  private modalRef: NgbModalRef;

  constructor(private _activatedRoute: ActivatedRoute,
              private _router: Router,
              private _bgpService: BgpService,
              private _modalService: NgbModal) {
  }

  ngOnInit() {
    this._activatedRoute.queryParams
      .subscribe(params => {
        this.asn = params['asn'];
        this.prefix = params['prefix']
        this.loadAnnouncementPreview();
      });
  }

  loadAnnouncementPreview():void {
    if (this.prefix && this.asn) {
      this.getAnnouncementPreviewData();
    } else {
      // open modal dialog
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
    setTimeout(() => {
      this.modalRef = this._modalService.open(modalTempRef);
      this.modalRef.result.then(result => {
          this._router.navigate(['/announcement-preview/'], {queryParams: {asn: this.asn, prefix: this.prefix}});
          this.modalRef.close();
      }, dismiss => {
        this.modalRef.close();
        this._router.navigate(['/bgp-preview']);
      });
    });
  }
}
