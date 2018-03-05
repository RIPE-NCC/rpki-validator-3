import {Component, Input, OnInit} from '@angular/core';

import {IValidationCheck} from "./validation-detail.model";
import {ManagingTable} from "../../shared/managing-table";
import {TrustAnchorsService} from "../../core/trust-anchors.service";

@Component({
  selector: 'validation-details',
  templateUrl: './validation-details.component.html',
  styleUrls: ['./validation-details.component.scss']
})
export class ValidationDetailsComponent extends ManagingTable implements OnInit {

  @Input() trustAnchorId: string;

  validationChecks: IValidationCheck[] = [];

  constructor(private _trustAnchorsService: TrustAnchorsService) {
    super();
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.setNumberOfFirstItemInTable();
    this._trustAnchorsService.getTrustAnchorValidationChecks(this.trustAnchorId,
                                                              this.firstItemInTable.toString(),
                                                              this.rowsPerPage.toString(),
                                                              this.searchBy,
                                                              this.sortBy,
                                                              this.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.validationChecks = response.data.validationChecks;
          this.numberOfItemsOnCurrentPage = this.validationChecks.length;
          this.totalItems = response.metadata.totalCount;
          this.setNumberOfLastItemInTable();
          if (!this.absolutItemsNumber)
            this.absolutItemsNumber = this.totalItems;
        });
  }
}
