import {Component, OnInit} from '@angular/core';
import {RoasService} from "./roas.service";
import {IRoa} from "./roas-response";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = true;
  errorMessage: string;
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
        this.roas = response.data,
        this.setPaginationParameters()
      },
      error => this.errorMessage = <any>error);
  }

  setPaginationParameters() {
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
