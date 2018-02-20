import {Component, OnInit} from '@angular/core';

import {RoasService} from "./roas.service";
import {IRoa} from "./roas";
import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchorResponse} from "../trust-anchors/trust-anchor";
import {ManagingTable} from "../shared/managing-table";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent extends ManagingTable implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = true;
  alertListValidatedTA: string;
  errorMessage: string;
  roas: IRoa[] = [];

  constructor(private _roasService: RoasService, private _trustAnchorsService: TrustAnchorsService) {
    super();
  }

  ngOnInit() {
    this.loadData();
    this.getValidatedTAForAlert();
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
        },
        error => this.errorMessage = <any>error);
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
}
