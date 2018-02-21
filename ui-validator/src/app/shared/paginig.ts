// PAGINATION
export abstract class Paging {

  rowsPerPage: number = 10;
  // total number of items before filter
  absolutItemsNumber: number;
  // total number of items in response (can be filtered)
  totalItems: number;
  numberOfItemsOnCurrentPage: number;
  page: number = 1;
  previousPage: number = 1;
  firstItemInTable: number = 0;
  lastItemInTable: number;

  abstract loadData();

  resetInitialValuesPagination(): void {
    this.page = this.previousPage = this.firstItemInTable = this.lastItemInTable = 0;
  }

  setNumberOfFirstItemInTable() {
    this.firstItemInTable = (this.page - 1) * this.rowsPerPage;
  }

  setNumberOfLastItemInTable() {
    this.lastItemInTable = this.firstItemInTable + this.numberOfItemsOnCurrentPage;
  }

  onChangedPageSize(pageSize: number): void {
    this.page = Math.floor(this.firstItemInTable / pageSize) + 1;
    this.rowsPerPage = +pageSize;
    this.loadData();
  }

  onChangePage(page: number): void {
    if (this.previousPage !== page) {
      this.previousPage = this.page = page;
      this.loadData();
    }
  }
}
