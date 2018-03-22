import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

import {Paging} from "./paging";
import {PagingDetailsModel} from "./paging-details.model";
import {IResponse} from "../response.model";

@Component({
  selector: 'toolbar',
  templateUrl: './toolbar.component.html'
})
export class ToolbarComponent extends Paging implements OnInit {

  @Input()
  loading: boolean;
  @Input('responseData')
  set responseData(response: IResponse) {
    if(response) {
      this.setLoadedDataParameters(response.data.length, response.metadata.totalCount, this.searchBy !== '');
    }
  }
  @Input()
  msgNoItems: string;
  @Output()
  notifyToolbarChanged: EventEmitter<PagingDetailsModel> = new EventEmitter<PagingDetailsModel>();
  pagingDetails: PagingDetailsModel;
  searchBy: string = '';

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.notifyParentAboutToolbarChange();
  }

  onChangedPageSize(pageSize: number): void {
    this.page = Math.floor(this.firstItemInTable / pageSize) + 1;
    this.rowsPerPage = +pageSize;
    this.notifyParentAboutToolbarChange();
  }

  onChangePage(page: number): void {
    if (this.previousPage !== page) {
      this.previousPage = this.page = page;
      this.notifyParentAboutToolbarChange();
    }
  }

  onChangedFilterBy(searchBy: string): void {
    this.resetInitialValuesPagination();
    this.searchBy = searchBy;
    this.notifyParentAboutToolbarChange();
  }

  private notifyParentAboutToolbarChange(): void {
    this.setNumberOfFirstItemInTable();
    this.pagingDetails = {
      firstItemInTable: this.firstItemInTable.toString(),
      rowsPerPage: this.rowsPerPage.toString(),
      searchBy: this.searchBy
    }
    this.notifyToolbarChanged.emit(this.pagingDetails);
  }
}
