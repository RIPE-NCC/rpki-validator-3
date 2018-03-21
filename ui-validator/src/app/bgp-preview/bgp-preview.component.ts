import {Component, OnInit, ViewChild} from '@angular/core';

import {BgpService} from "../core/bgp.service";
import {IBgp} from "./bgp.model";
import {Router} from "@angular/router";
import {ToolbarComponent} from "../shared/toolbar/toolbar.component";
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";

@Component({
  selector: 'app-bgp-preview',
  templateUrl: './bgp-preview.component.html',
  styleUrls: ['./bgp-preview.component.scss']
})
export class BgpPreviewComponent implements OnInit {

  pageTitle: string = "Nav.TITLE_BGP_PREVIEW";
  bgps: IBgp[] = [];

  @ViewChild(ToolbarComponent) toolbar: ToolbarComponent;

  constructor(private _bgpService: BgpService,
              private _router: Router) {
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.toolbar.loading = true;
    this.toolbar.setNumberOfFirstItemInTable();
    this._bgpService.getBgp(this.toolbar.firstItemInTable.toString(),
                            this.toolbar.rowsPerPage.toString(),
                            this.toolbar.searchBy,
                            this.toolbar.sortBy,
                            this.toolbar.sortDirection)
      .subscribe(
        response => {
          this.toolbar.loading = false;
          this.bgps = response.data;
          this.toolbar.setLoadedDataParameters(this.bgps.length, response.metadata.totalCount);
        });
  }

  openAnnouncementPreviewDetails(bgp: IBgp) {
    this._router.navigate(['/announcement-preview/'], { queryParams: { asn: bgp.asn, prefix: bgp.prefix} });
  }

  onToolbarChange() {
    this.loadData();
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.toolbar.setColumnSortedInfo(sort);
    this.loadData();
  }
}
