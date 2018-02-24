import {Component, OnInit} from '@angular/core';

import {BgpService} from "./bgp.service";
import {IBgp} from "./bgp.model";
import {ManagingTable} from "../shared/managing-table";

@Component({
  selector: 'app-bgp-preview',
  templateUrl: './bgp-preview.component.html',
  styleUrls: ['./bgp-preview.component.scss']
})
export class BgpPreviewComponent extends ManagingTable implements OnInit {

  pageTitle: string = "Nav.TITLE_BGP_PREVIEW";
  alertShown: boolean = true;
  bgps: IBgp[] = [];

  constructor(private _bgpService: BgpService) {
    super();
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this._bgpService.getBgp()
      .subscribe(
        response => {
          this.loading = false;
          this.bgps = response.data;
          this.numberOfItemsOnCurrentPage = this.bgps.length;
          this.totalItems = response.metadata.totalCount;
          this.setNumberOfLastItemInTable();
          if (!this.absolutItemsNumber)
            this.absolutItemsNumber = this.totalItems;
        },
        error => console.log(error)
        );
  }
}
