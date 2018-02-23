import {Component, OnInit} from '@angular/core';

import {RoasService} from "./roas.service";
import {IRoa} from "./roas.model";
import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchorsResponse} from "../trust-anchors/trust-anchor.model";
import {ManagingTable} from "../shared/managing-table";

@Component({
  selector: 'app-roas',
  templateUrl: './roas.component.html',
  styleUrls: ['./roas.component.scss']
})
export class RoasComponent extends ManagingTable implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = true;
  alertListValidatedTA: string;
  roas: IRoa[] = [];

  constructor(private _roasService: RoasService, private _trustAnchorsService: TrustAnchorsService) {
    super();
  }

  ngOnInit() {
    this.loadData();
    this.getNotValidatedTAForAlert();
  }

  loadData() {
    this.loading = true;
    this.setNumberOfFirstItemInTable();
    this._roasService.getRoas(this.firstItemInTable.toString(),
                              this.rowsPerPage.toString(),
                              this.searchBy,
                              this.sortBy,
                              this.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.roas = response.data;
          this.numberOfItemsOnCurrentPage = this.roas.length;
          this.totalItems = response.metadata.totalCount;
          this.setNumberOfLastItemInTable();
          if (!this.absolutItemsNumber)
            this.absolutItemsNumber = this.totalItems;
        });
  }

  getNotValidatedTAForAlert() {
    let listTa: string[] = [];
    this._trustAnchorsService.getTrustAnchors()
      .subscribe(
        response => {
          const taResponse = response as ITrustAnchorsResponse;
          taResponse.data.forEach(ta => {
            if (!ta.initialCertificateTreeValidationRunCompleted)
              listTa.push(ta.name);
          });
          this.alertListValidatedTA = listTa.join(', ');
          // don't show alert if all ta are validated
          this.alertShown = this.alertListValidatedTA.length > 0;
        }
      );
  }
}
