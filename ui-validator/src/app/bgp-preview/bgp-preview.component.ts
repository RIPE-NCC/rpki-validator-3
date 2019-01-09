import {Component, OnInit} from '@angular/core';

import {BgpService} from '../core/bgp.service';
import {IBgp} from './bgp.model';
import {Router} from '@angular/router';
import {ColumnSortedEvent} from '../shared/sortable-table/sort.service';
import {PagingDetailsModel} from '../shared/toolbar/paging-details.model';
import {IResponse} from '../shared/response.model';

@Component({
  selector: 'app-bgp-preview',
  templateUrl: './bgp-preview.component.html',
  styleUrls: ['./bgp-preview.component.scss']
})
export class BgpPreviewComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_BGP_PREVIEW';
  loading: boolean = true;
  bgps: IBgp[] = [];
  lastModified : string;
  alertShown: boolean = true;
  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _bgpService: BgpService,
              private _router: Router) {
  }

  ngOnInit() {
  }

  loadData() {
    this.loading = true;
    this._bgpService.getBgp(this.pagingDetails.firstItemInTable,
                            this.pagingDetails.rowsPerPage,
                            this.pagingDetails.searchBy,
                            this.sortTable.sortColumn,
                            this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.bgps = response.data;

          this.lastModified = this.elapsedTimeString(response.metadata.lastModified);
          this.response = response;
        });
  }

  openAnnouncementPreviewDetails(bgp: IBgp) {
    this._router.navigate(['/announcement-preview/'], { queryParams: { asn: bgp.asn, prefix: bgp.prefix} });
  }

  onToolbarChange(pagingDetails: PagingDetailsModel) {
    this.pagingDetails = pagingDetails;
    this.loadData();
  }

  elapsedTimeString(lastModified: number) {
    let now = new Date().getTime();
    let modified = new Date(lastModified).getTime();
    let diff = now-modified;
    let hoursDiff = Math.floor(diff/(60*60*1000));
    let minDiff = Math.floor((diff % (60*60*1000))/(60*1000));
    return hoursDiff + " hours " + minDiff+" minutes ago.";
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.sortTable = sort;
    this.loadData();
  }
}
