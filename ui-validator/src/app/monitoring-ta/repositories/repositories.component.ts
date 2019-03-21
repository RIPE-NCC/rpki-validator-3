import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

import {IRepositoriesStatuses, IRepository} from './repositories.model';
import {TrustAnchorsService} from '../../core/trust-anchors.service';
import {ColumnSortedEvent} from '../../shared/sortable-table/sort.service';
import {PagingDetailsModel} from '../../shared/toolbar/paging-details.model';
import {IResponse} from '../../shared/response.model';
import {RpkiToastrService} from "../../core/rpki-toastr.service";

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
  deleting: boolean = false;

  constructor(private _trustAnchorsService: TrustAnchorsService,
              private _toastr: RpkiToastrService) {
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
          // notify to refresh repositories statuses
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

  hasFailedRepo(){
    return this.repositories.filter((x => x.status === 'FAILED' )).length > 0;
  }


  deleteFilter(repo : IRepository){
    if(!this.deleting){
      this.deleting = true;
      this._trustAnchorsService.deleteRepository(repo)
        .subscribe(
          response => {
            this.loadData();
            this._toastr.info('Repositories.TOASTR_MSG_DELETED');
            this.deleting = false;
          }, error => {
            this._toastr.error('Repositories.TOASTR_MSG_DELETED_ERROR');
            this.deleting = false;
          }
        );
    }
  }

}
