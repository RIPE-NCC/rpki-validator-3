// PAGINATION
export class Paging {

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

  constructor() {
    this.setNumberOfFirstItemInTable();
  }

  resetInitialValuesPagination(): void {
    this.page = this.previousPage = this.firstItemInTable = this.lastItemInTable = 1;
  }

  setLoadedDataParameters(numberOfItemsOnCurrentPage: number, totalItems: number, filtered: boolean) {
    this.numberOfItemsOnCurrentPage = numberOfItemsOnCurrentPage;
    this.totalItems = totalItems;
    this.setNumberOfLastItemInTable();
    if (!filtered)
      this.absolutItemsNumber = this.totalItems;
  }

  setNumberOfFirstItemInTable() {
    this.firstItemInTable = (this.page - 1) * this.rowsPerPage;
  }

  setNumberOfLastItemInTable() {
    this.lastItemInTable = this.firstItemInTable + this.numberOfItemsOnCurrentPage;
  }
}
