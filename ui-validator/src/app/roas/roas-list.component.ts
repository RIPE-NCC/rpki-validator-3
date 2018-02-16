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
  // SEARCH
  searchBy: string = '';
  noFilteredRoas: boolean;
  // SORTING
  sortBy: string = 'asn';
  sortDirection: string = 'asc';
  // PAGINATION
  roasPerPage: number = 10;
  // total number of roas before filter
  absolutRoasNumber: number;
  // total number of roas in response (can be filtered)
  totalRoas: number;
  page: number = 1;
  previousPage: number = 1;
  firstRoaInTable: number = 0;
  lastRoaInTable: number;
  //LOADING
  loading: boolean = true;

  constructor(private _roasService: RoasService, private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.loadData();
    this.getValidatedTAForAlert();
  }

  loadData() {
    this.loading = true;
    this.setNumberOfFirstRoaInTable();
    this._roasService.getRoas(this.firstRoaInTable.toString(),
                              this.roasPerPage.toString(),
                              this.searchBy,
                              this.sortBy,
                              this.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.roas = response.data;
          this.noFilteredRoas = this.roas.length === 0;
          this.totalRoas = response.metadata.totalCount;
          this.setNumberOfLastRoaInTable();
          if (!this.absolutRoasNumber)
            this.absolutRoasNumber = this.totalRoas;
        },
        error => this.errorMessage = <any>error);
  }

  resetInitialValuesPagination(): void {
    this.page = this.previousPage = this.firstRoaInTable = this.lastRoaInTable = 0;
  }

  setNumberOfFirstRoaInTable() {
    this.firstRoaInTable = (this.page - 1) * this.roasPerPage;
  }

  setNumberOfLastRoaInTable() {
    this.lastRoaInTable = this.firstRoaInTable + this.roas.length;
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
    this.sortBy = $event.sortColumn;
    this.sortDirection = $event.sortDirection;
    this.loadData();
  }
}
