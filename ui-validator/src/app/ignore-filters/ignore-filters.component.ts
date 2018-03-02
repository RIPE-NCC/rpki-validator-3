import {Component, OnInit, Output} from '@angular/core';
import {Filter} from "./filter";
import {ManagingTable} from "../shared/managing-table";

@Component({
  selector: 'app-ignore-filters',
  templateUrl: './ignore-filters.component.html',
  styleUrls: ['./ignore-filters.component.scss']
})
export class IgnoreFiltersComponent extends ManagingTable implements OnInit {

  @Output() filter: Filter;
  pageTitle: string = 'Nav.TITLE_IGNORE_FILTERS';
  alertShown = true;

  constructor() {
    super();
    this.filter = new Filter();
  }

  ngOnInit() {
    this.loadData();
  }

  onFilterSubmit(filter: Filter) {
    console.log("This is what I submit" + filter)
  }

  loadData() {
  }
}
