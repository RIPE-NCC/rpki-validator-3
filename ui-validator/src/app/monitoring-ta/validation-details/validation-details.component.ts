import {Component, Input, OnInit, ViewChild} from '@angular/core';

import {IValidationCheck, IValidationChecksResponse} from "./validation-detail.model";
import {TrustAnchorsService} from "../../core/trust-anchors.service";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {ColumnSortedEvent} from "../../shared/sortable-table/sort.service";

@Component({
  selector: 'validation-details',
  templateUrl: './validation-details.component.html',
  styleUrls: ['./validation-details.component.scss']
})
export class ValidationDetailsComponent implements OnInit {

  @Input() trustAnchorId: string;

  validationChecks: IValidationCheck[] = [];
  validationChecksResponse: IValidationChecksResponse;

  @ViewChild(ToolbarComponent) toolbar: ToolbarComponent;

  constructor(private _trustAnchorsService: TrustAnchorsService) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.toolbar.loading = true;
    this.toolbar.setNumberOfFirstItemInTable();
    this._trustAnchorsService.getTrustAnchorValidationChecks(this.trustAnchorId,
                                                              this.toolbar.firstItemInTable.toString(),
                                                              this.toolbar.rowsPerPage.toString(),
                                                              this.toolbar.searchBy,
                                                              this.toolbar.sortBy,
                                                              this.toolbar.sortDirection)
      .subscribe(
        response => {
          this.toolbar.loading = false;
          this.validationChecks = response.data.validationChecks;
          this.validationChecksResponse = response;
          this.toolbar.setLoadedDataParameters(this.validationChecks.length, response.metadata.totalCount);
        });
  }

  onToolbarChange() {
    this.loadData();
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.toolbar.setColumnSortedInfo(sort);
    this.loadData();
  }
}
