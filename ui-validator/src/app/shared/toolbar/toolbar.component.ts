import {Component, EventEmitter, OnInit, Output} from '@angular/core';

import {Paging} from "./paging";
import {ColumnSortedEvent} from "../sortable-table/sort.service";

@Component({
  selector: 'toolbar',
  templateUrl: './toolbar.component.html'
})
export class ToolbarComponent extends Paging implements OnInit {

  // SORTING
  sortBy: string = '';
  sortDirection: string = 'asc';
  // SEARCH
  searchBy: string = '';
  // LOADING
  loading: boolean = true;

  @Output() notifyToolbarChanged: EventEmitter<any> = new EventEmitter<any>();

  constructor() {
    super();
  }

  ngOnInit(): void {
  }

  onChangedPageSize(pageSize: number): void {
    this.page = Math.floor(this.firstItemInTable / pageSize) + 1;
    this.rowsPerPage = +pageSize;
    this.notifyToolbarChanged.emit();
  }

  onChangePage(page: number): void {
    if (this.previousPage !== page) {
      this.previousPage = this.page = page;
      this.notifyToolbarChanged.emit();
    }
  }

  onChangedFilterBy(searchBy: string): void {
    this.resetInitialValuesPagination();
    this.searchBy = searchBy;
    this.notifyToolbarChanged.emit();
  }

  setLoading(loading: boolean): void {
    this.loading = loading;
  }

  setColumnSortedInfo(sort: ColumnSortedEvent) {
    this.sortBy = sort.sortColumn;
    this.sortDirection = sort.sortDirection;
  }

  addNewItemToTable(): void {
    this.absolutItemsNumber++;
  }

  removedItemFromTable(): void {
    this.absolutItemsNumber--;
  }
}
