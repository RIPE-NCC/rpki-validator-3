import {Component, OnInit} from '@angular/core';
import {NgForm} from '@angular/forms';

import {IWhitelistEntry, WhitelistService} from './whitelist.service';
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";
import {PagingDetailsModel} from "../shared/toolbar/paging-details.model";
import {IResponse} from "../shared/response.model";
import {RpkiToastrService} from "../core/rpki-toastr.service";

@Component({
  selector: 'app-whitelist',
  templateUrl: './whitelist.component.html',
  styleUrls: ['./whitelist.component.scss']
})
export class WhitelistComponent implements OnInit {

  ipv4RegExp: RegExp = new RegExp('^([0-9]{1,3}\\.){3}[0-9]{1,3}(\\/([0-9]|[1-2][0-9]|3[0-2]))?$');
  ipv6RegExp: RegExp = new RegExp('^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$');
  asnRegExp: RegExp = new RegExp('^([AS|as|aS|As]{2})?[0-9]{1,9}$');

  pageTitle: string = 'Nav.TITLE_WHITELIST';
  loading: boolean = true;
  validPrefix: boolean = true;
  validAsn: boolean = true;
  validMaxLengthIpv4: boolean = true;
  validMaxLengthIpv6: boolean = true;
  validMaxLengthInRange: boolean = true;
  // button is submitted until waiting for response
  submitted: boolean = false;
  deleting: boolean = false;
  alertShown: boolean = true;
  whitelist: IWhitelistEntry[] = [];
  whitelistEntry: IWhitelistEntry;

  response: IResponse;
  sortTable: ColumnSortedEvent = {sortColumn: '', sortDirection: 'asc'};
  pagingDetails: PagingDetailsModel;

  constructor(private _whitelistService: WhitelistService,
              private _toastr: RpkiToastrService) {
  }

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
          this.response = response;
        });
  }

  onUploadSlurm(): void {
    this.loadData();
  }

  onWhitelistEntrySubmit(form: NgForm): void {
    const entry: IWhitelistEntry = form.value;
    this.validatePrefix(entry.prefix);
    this.validateAsn(entry.asn);
    if (this.validPrefix && this.validAsn) {
      this.submitted = true;
      this._whitelistService.saveWhitelistEntry(entry)
        .subscribe(
          response => {
            this.loadData();
            form.resetForm();
            this.submitted = false;
            this._toastr.success('Whitelist.TOASTR_MSG_ADDED');
          }, error => {
            this.submitted = false;
            this._toastr.error('Whitelist.TOASTR_MSG_ADD_ERROR');
            this.validPrefix = !(error.error.errors.some(err => err.source.pointer === '/data/prefix'));
            this.validAsn = !error.error.errors.some(err => err.source.pointer === '/data/asn');
            this.validMaxLengthIpv4 = !error.error.errors.some(err => err.detail === 'MAX_LENGTH_LONGER_32');
            this.validMaxLengthIpv6 = !error.error.errors.some(err => err.detail === 'MAX_LENGTH_LONGER_128');
            this.validMaxLengthInRange = !error.error.errors.some(err => err.detail === 'MAX_LENGTH_LONGER_PREFIX_LENGTH');
          }
        );
    }
  }

  deleteFilter(entry: IWhitelistEntry): void {
    if (!this.deleting) {
      this.deleting = true;
      this._whitelistService.deleteWhitelistEntry(entry)
        .subscribe(
          response => {
            this.loadData();
            this._toastr.info('Whitelist.TOASTR_MSG_DELETED');
            this.deleting = false;
          }, error => {
            this._toastr.error('Whitelist.TOASTR_MSG_DELETED_ERROR');
            this.deleting = false;
          }
        );
    }
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

  showToastrMsgAddDisable(disable: boolean): void {
    if (disable) {
      this._toastr.info('Whitelist.TOASTR_MSG_REQUIRED_PREFIX_AND_ASN');
    }
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
