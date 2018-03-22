import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

import {IRepositoriesStatuses, IRepository} from "./repositories.model";
import {TrustAnchorsService} from "../../core/trust-anchors.service";
import {ColumnSortedEvent} from "../../shared/sortable-table/sort.service";
import {PagingDetailsModel} from "../../shared/toolbar/paging-details.model";
import {IResponse} from "../../shared/response.model";

@Component({
  selector: 'repositories',
  templateUrl: './repositories.component.html',
  styleUrls: ['./repositories.component.scss']
})
export class RepositoriesComponent implements OnInit {

  @Input() trustAnchorId: string;
  @Output() notifyLoadedStatuses: EventEmitter<IRepositoriesStatuses> = new EventEmitter<IRepositoriesStatuses>();

  loading: boolean = true;
  repositories: IRepository[] = [];
  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
  }

  loadData() {
    this.loading = true;
    this._trustAnchorsService.getRepositories(this.trustAnchorId,
                                              this.pagingDetails.firstItemInTable,
                                              this.pagingDetails.rowsPerPage,
                                              this.pagingDetails.searchBy,
                                              this.sortTable.sortColumn,
                                              this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.repositories = response.data;
          this.response = response;
          // TODO check if there is really need for this event, maybe you can call it immidiatly
          this.notifyLoadedStatuses.emit();
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
