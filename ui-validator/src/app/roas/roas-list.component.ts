import {Component, OnInit} from '@angular/core';
import {RoasService} from "./roas.service";
import {IRoa, IRoasResponse} from "./roas-response";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = true;
  errorMessage: string;
  response: IRoasResponse;
  roas: IRoa[] = [];
  pageSizes: number[] = [ 10, 25, 50, 100];
  // search
  searchBy: string;
  // pagination
  roasPerPage: number = 10;
  totalRoas: number;
  page: number = 1;
  previousPage: number;
  firstRoaInTable: number = 1;
  lastRoaInTable: number = 10;

  constructor(private _roasService: RoasService) {
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this._roasService.getRoas(this.firstRoaInTable.toString(), this.roasPerPage.toString()).subscribe(
      response => {
        this.response = response;
        this.roas = response.data,
        this.setPaginationParameters()
      },
      error => this.errorMessage = <any>error);
  }

  // FIXME getQueryString should be REMOVED AS SOON AS totalRoas become available from backend
  getTotalNumberOfRoas() {
    const linkToLastPage: string = this.response.links.last;
    const firstRoaOnLastPage = RoasListComponent.getQueryString('startFrom', linkToLastPage);
    this._roasService.getRoas(firstRoaOnLastPage, this.roasPerPage.toString()).subscribe(
      response => {
        this.totalRoas = +firstRoaOnLastPage + response.data.length;
      },
      error => this.errorMessage = <any>error);
  }

  // FIXME getQueryString should be REMOVED AS SOON AS totalRoas become available from backend
  static getQueryString(field: string, url: string): string {
    const reg = new RegExp('[?&]' + field + '=([^&#]*)', 'i');
    const string = reg.exec(url);
    return string ? string[1] : null;
  };

  setPaginationParameters() {
    this.getTotalNumberOfRoas();
    this.setNumberOfFirstRoaInTable();
    this.setNumberOfLastRoaInTable();
  }

  setNumberOfFirstRoaInTable() {
    this.firstRoaInTable = (this.page - 1) * this.roasPerPage + 1;
  }

  setNumberOfLastRoaInTable() {
    this.lastRoaInTable = this.firstRoaInTable + this.roasPerPage - 1;
  }

  onChangePageSize(pageSize: number): void {
    this.page = Math.floor(this.firstRoaInTable/pageSize) + 1;
    this.roasPerPage = +pageSize;
    this.loadData();
  }

  onChangePage(page: number): void {
    if (page !== this.previousPage) {
      this.previousPage = page;
      this.loadData();
    }
  }

  onSearchByClick(searchBy: string): void {
    this.searchBy = searchBy;
    // TODO call backend for roas and set rsponse into this.roas
  }

}
