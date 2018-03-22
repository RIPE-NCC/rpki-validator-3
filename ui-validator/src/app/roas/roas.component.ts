import {Component, OnInit} from '@angular/core';

import {RoasService} from './roas.service';
import {IRoa} from './roas.model';
import {TrustAnchorsService} from '../core/trust-anchors.service';
import {ColumnSortedEvent} from '../shared/sortable-table/sort.service';
import {PagingDetailsModel} from "../shared/toolbar/paging-details.model";
import {IResponse} from "../shared/response.model";

@Component({
  selector: 'app-roas',
  templateUrl: './roas.component.html',
  styleUrls: ['./roas.component.scss']
})
export class RoasComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  loading: boolean = true;
  alertShown: boolean = false;
  alertListValidatedTA: string;
  roas: IRoa[] = [];
  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _roasService: RoasService,
              private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.getNotValidatedTAForAlert();
  }

  loadData() {
    this.loading = true;
    this._roasService.getRoas(this.pagingDetails.firstItemInTable,
                              this.pagingDetails.rowsPerPage,
                              this.pagingDetails.searchBy,
                              this.sortTable.sortColumn,
                              this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.roas = response.data;
          this.response = response;
        });
  }

  // values for alert messages
  getNotValidatedTAForAlert() {
    let listTa: string[] = [];
    this._trustAnchorsService.getTrustAnchors()
      .subscribe(
        response => {
          const taResponse = response as IResponse;
          taResponse.data.forEach(ta => {
            if (!ta.initialCertificateTreeValidationRunCompleted)
              listTa.push(ta.name);
          });
          this.alertListValidatedTA = listTa.join(', ');
          // don't show alert if all ta are validated
          this.alertShown = this.alertListValidatedTA.length > 0;
        }
      );
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
