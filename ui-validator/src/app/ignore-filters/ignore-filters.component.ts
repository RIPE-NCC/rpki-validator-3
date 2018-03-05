import {Component, OnInit} from '@angular/core';

import {ManagingTable} from "../shared/managing-table";
import {IgnoreFiltersService} from "./ignore-filters.service";
import {IIgnoreFilter} from "./filters.model";

@Component({
  selector: 'app-ignore-filters',
  templateUrl: './ignore-filters.component.html',
  styleUrls: ['./ignore-filters.component.scss']
})
export class IgnoreFiltersComponent extends ManagingTable implements OnInit {

  ipv4RegExp: RegExp = new RegExp('^(?=\\d+\\.\\d+\\.\\d+\\.\\d+($|\\/))(([1-9]?\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.?){4}(\\/([0-9]|[1-2][0-9]|3[0-2]))?$');
  ipv6RegExp: RegExp = new RegExp('^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$');
  asnRegExp: RegExp = new RegExp("^([1-5]\\d{4}|[1-9]\\d{0,3}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(\\.([1-5]\\d{4}|[1-9]\\d{0,3}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]|0))?$");

  pageTitle: string = 'Nav.TITLE_IGNORE_FILTERS';
  validPrefix: boolean = true;
  validAsn: boolean = true;
  alertShown: boolean = true;
  alertSuccessAdded: boolean = false;
  alertSuccessDeleted: boolean = false;
  mouseoverAdd: boolean = false;
  ignoreFilters: IIgnoreFilter[] = [];

  constructor(private _ignoreFiltersService: IgnoreFiltersService) {
    super();
  }

  ngOnInit() {
    this.loadData();
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

  loadData() {
    this._ignoreFiltersService.getIgnoreFilters(this.firstItemInTable.toString(),
                                                this.rowsPerPage.toString(),
                                                this.searchBy,
                                                this.sortBy,
                                                this.sortDirection)
        .subscribe(
            response => {
              this.loading = false;
              this.ignoreFilters = response.data.ignoreFilters;
              this.numberOfItemsOnCurrentPage = this.ignoreFilters.length;
              this.totalItems = response.metadata.totalCount;
              this.setNumberOfLastItemInTable();
              if (!this.absolutItemsNumber)
                this.absolutItemsNumber = this.totalItems;
            });
        }
  //TODO make it really work
  onFilterSubmit(filter: IIgnoreFilter): void {
    this.validatePrefix(filter.prefix);
    this.validateAsn(filter.asn);
    this._ignoreFiltersService.saveIgnoreFilter(filter)
      .subscribe(
        response => {
          this.alertSuccessAdded = true;
        }
      );
  }

  //TODO make it really work
  deleteFilter(filter: IIgnoreFilter): void {
    this._ignoreFiltersService.deleteIgnoreFilter(filter)
      .subscribe(
        response => {
          this.alertSuccessDeleted = true;
        }
      );
  }
}
