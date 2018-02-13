import {Component, OnInit} from '@angular/core';

import {RoasService} from "./roas.service";
import {IRoa, IRoasResponse} from "./roas";
import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchorResponse} from "../trust-anchors/trust-anchor";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = true;
  alertListValidatedTA: string;
  errorMessage: string;
  response: IRoasResponse;
  roas: IRoa[] = [];
  pageSizes: number[] = [10, 25, 50, 100];
  // search
  searchBy: string = '';
  // pagination
  roasPerPage: number = 10;
  totalRoas: number;
  page: number = 1;
  previousPage: number = 1;
  firstRoaInTable: number = 1;
  lastRoaInTable: number = 10;

  constructor(private _roasService: RoasService, private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.loadData();
    this.getValidatedTAForAlert();
  }

  loadData() {
    this.setPaginationParameters();
    this._roasService.getRoas(this.firstRoaInTable.toString(), this.roasPerPage.toString(), this.searchBy)
      .subscribe(
        response => {
          this.roas = response.data;
          // TODO following 2 lines should be deleted with getTotalNumberOfRoas
          this.response = response;
          this.getTotalNumberOfRoas();
        },
        error => this.errorMessage = <any>error);
  }

  // FIXME getQueryString should be REMOVED AS SOON AS totalRoas become available from backend
  getTotalNumberOfRoas() {
    const linkToLastPage: string = this.response.links.last;
    const firstRoaOnLastPage = this.getQueryString('startFrom', linkToLastPage);
    this._roasService.getRoas(firstRoaOnLastPage, this.roasPerPage.toString(), this.searchBy)
      .subscribe(
        response => {
          this.totalRoas = +firstRoaOnLastPage + response.data.length;
        },
        error => this.errorMessage = <any>error);
  }

  // TODO getQueryString should be REMOVED AS SOON AS totalRoas become available from backend
  getQueryString(field: string, url: string): string {
    const reg = new RegExp('[?&]' + field + '=([^&#]*)', 'i');
    const value = reg.exec(url);
    return value ? value[1] : null;
  };

  resetInitialValuesPagination(): void {
    this.page = this.previousPage = this.firstRoaInTable = this.lastRoaInTable = 1;
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

  getValidatedTAForAlert() {
    let listTa: string[] = [];
    this._trustAnchorsService.getTrustAnchors()
      .subscribe(
        response => {
          const taResponse = response as ITrustAnchorResponse;
          taResponse.data.forEach(ta => {
            if (ta.initialCertificateTreeValidationRunCompleted)
              listTa.push(ta.name);
          });
          this.alertListValidatedTA = listTa.join(', ');
        }
      );
  }

  onChangePageSize(pageSize: number): void {
    this.page = Math.floor(this.firstRoaInTable / pageSize) + 1;
    this.roasPerPage = +pageSize;
    this.loadData();
  }

  onChangePage(page: number): void {
    if (this.previousPage !== page) {
      this.previousPage = this.page = page;
      this.loadData();
    }
  }

  onSearchByClick(searchBy: string): void {
    this.resetInitialValuesPagination();
    this.searchBy = searchBy;
    this.loadData();
  }

  onSorted($event): void {
    const sortColumn = $event.sortColumn;
    const asc_desc = $event.sortDirection;
    console.log('sorting ' + $event);
    // probably clean and set roas
  }
}
