import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

import {IRepositoriesStatuses, IRepository} from "./repositories.model";
import {ManagingTable} from "../../shared/managing-table";
import {TrustAnchorsService} from "../../core/trust-anchors.service";

@Component({
  selector: 'repositories',
  templateUrl: './repositories.component.html',
  styleUrls: ['./repositories.component.scss']
})
export class RepositoriesComponent extends ManagingTable implements OnInit {

  @Input() trustAnchorId: string;
  @Output() notify: EventEmitter<IRepositoriesStatuses> = new EventEmitter<IRepositoriesStatuses>();

  repositories: IRepository[] = [];

  constructor(private _trustAnchorsService: TrustAnchorsService) {
    super();
  }

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.setNumberOfFirstItemInTable();
    this._trustAnchorsService.getRepositories(this.trustAnchorId,
                                              this.firstItemInTable.toLocaleString(),
                                              this.rowsPerPage.toString(),
                                              this.searchBy,
                                              this.sortBy,
                                              this.sortDirection)
      .subscribe(
        response => {
          this.loading = false;
          this.repositories = response.data;
          this.notify.emit();
          this.numberOfItemsOnCurrentPage = this.repositories.length;
          this.totalItems = response.metadata.totalCount;
          this.setNumberOfLastItemInTable();
          if (!this.absolutItemsNumber)
            this.absolutItemsNumber = this.totalItems;
        });
  }
}
