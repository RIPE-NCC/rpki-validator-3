import {Component, Input, OnInit} from '@angular/core';

import {IValidationCheck} from './validation-detail.model';
import {TrustAnchorsService} from '../../core/trust-anchors.service';
import {ColumnSortedEvent} from '../../shared/sortable-table/sort.service';
import {IResponse} from '../../shared/response.model';
import {PagingDetailsModel} from '../../shared/toolbar/paging-details.model';

@Component({
  selector: 'validation-details',
  templateUrl: './validation-details.component.html',
  styleUrls: ['./validation-details.component.scss']
})
export class ValidationDetailsComponent implements OnInit {

  @Input() trustAnchorId: string;

  loading: boolean = true;
  validationChecks: IValidationCheck[] = [];

  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _trustAnchorsService: TrustAnchorsService) {}

  ngOnInit() {
  }

  loadData() {
    this.loading = true;
    this._trustAnchorsService.getTrustAnchorValidationChecks(this.trustAnchorId,
                                                            this.pagingDetails.firstItemInTable,
                                                            this.pagingDetails.rowsPerPage,
                                                            this.pagingDetails.searchBy,
                                                            this.sortTable.sortColumn,
                                                            this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.validationChecks = response.data;
          this.response = response;
        });
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
