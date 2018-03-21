import {Component, OnInit, ViewChild} from '@angular/core';
import {NgForm} from "@angular/forms";

import {IgnoreFiltersService} from "./ignore-filters.service";
import {IIgnoreFilter} from "./filters.model";
import {ToolbarComponent} from "../shared/toolbar/toolbar.component";
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";

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
  validPrefix: boolean = true;
  validAsn: boolean = true;
  alertShown: boolean = true;
  alertSuccessAdded: boolean = false;
  alertSuccessDeleted: boolean = false;
  mouseoverAdd: boolean = false;
  ignoreFilters: IIgnoreFilter[] = [];
  filter: IIgnoreFilter;

  @ViewChild(ToolbarComponent) toolbar: ToolbarComponent;

  constructor(private _ignoreFiltersService: IgnoreFiltersService) {
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.toolbar.loading = true;
    this.toolbar.setNumberOfLastItemInTable();
    this._ignoreFiltersService.getIgnoreFilters(this.toolbar.firstItemInTable.toString(),
                                                this.toolbar.rowsPerPage.toString(),
                                                this.toolbar.searchBy,
                                                this.toolbar.sortBy,
                                                this.toolbar.sortDirection)
      .subscribe(
        response => {
          this.toolbar.loading = false;
          this.ignoreFilters = response.data;
          this.toolbar.setLoadedDataParameters(this.ignoreFilters.length, response.metadata.totalCount);
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
            this.toolbar.addNewItemToTable();
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
          this.toolbar.removedItemFromTable()
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

  onToolbarChange() {
    this.loadData();
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.toolbar.setColumnSortedInfo(sort);
    this.loadData();
  }
}
