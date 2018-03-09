import {Component, OnInit} from '@angular/core';

import {BgpService} from "../core/bgp.service";
import {IBgp} from "./bgp.model";
import {ManagingTable} from "../shared/managing-table";
import {Router} from "@angular/router";

@Component({
  selector: 'app-bgp-preview',
  templateUrl: './bgp-preview.component.html',
  styleUrls: ['./bgp-preview.component.scss']
})
export class BgpPreviewComponent extends ManagingTable implements OnInit {

  pageTitle: string = "Nav.TITLE_BGP_PREVIEW";
  bgps: IBgp[] = [];

  constructor(private _bgpService: BgpService,
              private _router: Router) {
    super();
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.setNumberOfFirstItemInTable();
    this._bgpService.getBgp(this.firstItemInTable.toString(),
                            this.rowsPerPage.toString(),
                            this.searchBy,
                            this.sortBy,
                            this.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.bgps = response.data;
          this.numberOfItemsOnCurrentPage = this.bgps.length;
          this.totalItems = response.metadata.totalCount;
          this.setNumberOfLastItemInTable();
          if (!this.absolutItemsNumber)
            this.absolutItemsNumber = this.totalItems;
        }
        );
  }

  openAnnouncementPreviewDetails(bgp: IBgp) {
    this._router.navigate(['/announcement-preview/'], { queryParams: { asn: bgp.asn, prefix: bgp.prefix} });
  }
}
