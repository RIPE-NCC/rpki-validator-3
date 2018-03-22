import {Component, OnInit} from '@angular/core';
import {NgForm} from "@angular/forms";

import {IgnoreFiltersService, IIgnoreFilter} from "./ignore-filters.service";
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";
import {IResponse} from "../shared/response.model";
import {PagingDetailsModel} from "../shared/toolbar/paging-details.model";

@Component({
  selector: 'app-ignore-filters',
  templateUrl: './ignore-filters.component.html',
  styleUrls: ['./ignore-filters.component.scss']
})
export class IgnoreFiltersComponent implements OnInit {

  ipv4RegExp: RegExp = new RegExp('^([0-9]{1,3}\\.){3}[0-9]{1,3}(\\/([0-9]|[1-2][0-9]|3[0-2]))?$');
  ipv6RegExp: RegExp = new RegExp('^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$');
  asnRegExp: RegExp = new RegExp('^([AS]{2})?[0-9]{1,9}$');

  pageTitle: string = 'Nav.TITLE_IGNORE_FILTERS';
  loading: boolean = true;
  validPrefix: boolean = true;
  validAsn: boolean = true;
  alertShown: boolean = true;
  alertSuccessAdded: boolean = false;
  alertSuccessDeleted: boolean = false;
  mouseoverAdd: boolean = false;
  ignoreFilters: IIgnoreFilter[] = [];
  filter: IIgnoreFilter;
  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _ignoreFiltersService: IgnoreFiltersService) {
  }

  ngOnInit() {
  }

  loadData() {
    this.loading = true;
    this._ignoreFiltersService.getIgnoreFilters(this.pagingDetails.firstItemInTable,
                                                this.pagingDetails.rowsPerPage,
                                                this.pagingDetails.searchBy,
                                                this.sortTable.sortColumn,
                                                this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.ignoreFilters = response.data;
          this.response = response;
        }
      )
  }

  onFilterSubmit(form: NgForm): void {
    const filter: IIgnoreFilter = form.value;
    this.validatePrefix(filter.prefix);
    this.validateAsn(filter.asn);
    if (this.validPrefix && this.validAsn) {
      this._ignoreFiltersService.saveIgnoreFilter(filter)
        .subscribe(
          response => {
            this.clearAlerts();
            this.alertSuccessAdded = true;
            this.loadData();
            form.resetForm();
          }
        );
    }
  }

  deleteFilter(filter: IIgnoreFilter): void {
    this._ignoreFiltersService.deleteIgnoreFilter(filter)
      .subscribe(
        response => {
          this.clearAlerts();
          this.alertSuccessDeleted = true;
          this.loadData();
        }
      );
  }

  validatePrefix(prefix: string): void {
    if (prefix) {
      this.validPrefix = this.ipv4RegExp.test(prefix) || this.ipv6RegExp.test(prefix);
    }
  }

  validateAsn(asn: string): void {
    if (asn) {
      this.validAsn = this.asnRegExp.test(asn);
    }
  }

  clearAlerts() {
    this.alertSuccessAdded = false;
    this.alertSuccessDeleted = false;
  }

  onToolbarChange(pagingDetails: PagingDetailsModel) {
    this.pagingDetails = pagingDetails;
    this.loadData();
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.sortTable = sort;
    this.loadData();
  }
}
