import {Component, OnInit} from '@angular/core';
import {NgForm} from '@angular/forms';

import {IWhitelistEntry, WhitelistService} from './whitelist.service';
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";
import {PagingDetailsModel} from "../shared/toolbar/paging-details.model";
import {IResponse} from "../shared/response.model";

@Component({
  selector: 'app-whitelist',
  templateUrl: './whitelist.component.html',
  styleUrls: ['./whitelist.component.scss']
})
export class WhitelistComponent implements OnInit {

  ipv4RegExp: RegExp = new RegExp('^([0-9]{1,3}\\.){3}[0-9]{1,3}(\\/([0-9]|[1-2][0-9]|3[0-2]))?$');
  ipv6RegExp: RegExp = new RegExp('^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$');
  asnRegExp: RegExp = new RegExp('^([AS]{2})?[0-9]{1,9}$');
  maxLengthRegExp: RegExp = new RegExp('^1[2-9]|2[0-9]|3[0-2]$');

  pageTitle: string = 'Nav.TITLE_WHITELIST';
  loading: boolean = true;
  validPrefix: boolean = true;
  validAsn: boolean = true;
  validMaxLength: boolean = true;
  alertShown: boolean = true;
  alertSuccessAdded: boolean = false;
  alertSuccessDeleted: boolean = false;
  mouseoverAdd: boolean = false;
  whitelist: IWhitelistEntry[] = [];
  whitelistEntry: IWhitelistEntry;

  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _whitelistService: WhitelistService) { }

  ngOnInit() {
  }

  loadData() {
    this.loading = true;
    this._whitelistService.getWhitelist(this.pagingDetails.firstItemInTable,
                                        this.pagingDetails.rowsPerPage,
                                        this.pagingDetails.searchBy,
                                        this.sortTable.sortColumn,
                                        this.sortTable.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.whitelist = response.data;
          this.response = response
        });
  }

  onWhitelistEntrySubmit(form: NgForm): void {
    const entry: IWhitelistEntry = form.value;
    this.validatePrefix(entry.prefix);
    this.validateAsn(entry.asn);
    this.validateMaxLength(entry.maximumLength);
    if (this.validPrefix && this.validAsn && this.validMaxLength) {
      this._whitelistService.saveWhitelistEntry(entry)
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

  deleteFilter(entry: IWhitelistEntry): void {
    this._whitelistService.deleteWhitelistEntry(entry)
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

  validateMaxLength(maxLength: number): void {
    if (maxLength) {
      this.validMaxLength = this.maxLengthRegExp.test(String(maxLength));
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
