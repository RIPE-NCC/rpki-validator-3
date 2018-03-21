import {Component, OnInit, ViewChild} from '@angular/core';

import {RoasService} from "./roas.service";
import {IRoa} from "./roas.model";
import {TrustAnchorsService} from "../core/trust-anchors.service";
import {ITrustAnchorsResponse} from "../trust-anchors/trust-anchor.model";
import {ToolbarComponent} from "../shared/toolbar/toolbar.component";
import {ColumnSortedEvent} from "../shared/sortable-table/sort.service";

@Component({
  selector: 'app-roas',
  templateUrl: './roas.component.html',
  styleUrls: ['./roas.component.scss']
})
export class RoasComponent implements OnInit {

  pageTitle: string = 'Nav.TITLE_ROAS';
  alertShown = false;
  alertListValidatedTA: string;
  roas: IRoa[] = [];

  @ViewChild(ToolbarComponent) toolbar: ToolbarComponent;

  constructor(private _roasService: RoasService,
              private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.loadData();
    this.getNotValidatedTAForAlert();
  }

  loadData() {
    this.toolbar.setLoading(true);
    this.toolbar.setNumberOfFirstItemInTable();
    this._roasService.getRoas(this.toolbar.firstItemInTable.toString(),
                              this.toolbar.rowsPerPage.toString(),
                              this.toolbar.searchBy,
                              this.toolbar.sortBy,
                              this.toolbar.sortDirection)
      .subscribe(
        response => {
          this.toolbar.setLoading(false);
          this.roas = response.data;
          this.toolbar.setLoadedDataParameters(this.roas.length, response.metadata.totalCount);
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

  onToolbarChange() {
    this.loadData();
  }

  onSorted(sort: ColumnSortedEvent): void {
    this.toolbar.setColumnSortedInfo(sort);
    this.loadData();
  }
}
