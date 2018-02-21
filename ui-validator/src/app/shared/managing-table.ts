import {Paging} from "./paginig";

export abstract class ManagingTable extends Paging {
  // SEARCH
  searchBy: string = '';
  // SORTING
  sortBy: string = '';
  sortDirection: string = 'asc';
  //LOADING
  loading: boolean = true;

  onChangedFilterBy(searchBy: string): void {
    this.resetInitialValuesPagination();
    this.searchBy = searchBy;
    this.loadData();
  }

  onSorted($event): void {
    this.sortBy = $event.sortColumn;
    this.sortDirection = $event.sortDirection;
    this.loadData();
  }
}
