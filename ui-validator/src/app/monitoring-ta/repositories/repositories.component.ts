import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';

import {IRepositoriesStatuses, IRepository} from "./repositories.model";
import {TrustAnchorsService} from "../../core/trust-anchors.service";
import {ToolbarComponent} from "../../shared/toolbar/toolbar.component";
import {ColumnSortedEvent} from "../../shared/sortable-table/sort.service";

@Component({
  selector: 'repositories',
  templateUrl: './repositories.component.html',
  styleUrls: ['./repositories.component.scss']
})
export class RepositoriesComponent implements OnInit {

  @Input() trustAnchorId: string;
  @Output() notifyLoadedStatuses: EventEmitter<IRepositoriesStatuses> = new EventEmitter<IRepositoriesStatuses>();

  repositories: IRepository[] = [];

  @ViewChild(ToolbarComponent) toolbar: ToolbarComponent;

  constructor(private _trustAnchorsService: TrustAnchorsService) {
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.toolbar.loading = true;
    this.toolbar.setNumberOfFirstItemInTable();
    this._trustAnchorsService.getRepositories(this.trustAnchorId,
                                              this.toolbar.firstItemInTable.toString(),
                                              this.toolbar.rowsPerPage.toString(),
                                              this.toolbar.searchBy,
                                              this.toolbar.sortBy,
                                              this.toolbar.sortDirection)
      .subscribe(
        response => {
          this.toolbar.loading = false;
          this.repositories = response.data;
          this.notifyLoadedStatuses.emit();
          this.toolbar.setLoadedDataParameters(this.repositories.length, response.metadata.totalCount);
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
