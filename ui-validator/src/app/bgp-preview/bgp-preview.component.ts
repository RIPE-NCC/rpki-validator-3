import {Component, Input, OnInit} from '@angular/core';

import {BgpService} from '../core/bgp.service';
import {IBgp} from './bgp.model';
import {ActivatedRoute, Router} from '@angular/router';
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
  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;


  @Input()
  q: string;

  constructor(private _activatedRoute: ActivatedRoute,
              private _bgpService: BgpService,
              private _router: Router) {
  }

  ngOnInit() {
    this._activatedRoute.queryParams.subscribe(params => {
      this.q = params['q'];
    });
  }

  loadData() {
    this.loading = true;
    this._bgpService.getBgp(this.pagingDetails.firstItemInTable,
                            this.pagingDetails.rowsPerPage,
                            this.q ? this.q : this.pagingDetails.searchBy,
                            this.sortTable.sortColumn,
                            this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.bgps = response.data;
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

  onSorted(sort: ColumnSortedEvent): void {
    this.sortTable = sort;
    this.loadData();
  }
}
