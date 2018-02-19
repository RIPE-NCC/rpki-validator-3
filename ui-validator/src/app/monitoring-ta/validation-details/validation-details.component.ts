import { Component, OnInit } from '@angular/core';
import {IValidationDetails} from "./validation-details";

@Component({
  selector: 'validation-details',
  templateUrl: './validation-details.component.html',
  styleUrls: ['./validation-details.component.scss']
})
export class ValidationDetailsComponent implements OnInit {

  validationDetails: IValidationDetails;
  itemsPerPage: number = 10;
  // SEARCH
  searchBy: string = '';
  noFiltered: boolean;
  // SORTING
  sortBy: string = 'asn';
  sortDirection: string = 'asc';
  //LOADING
  loading: boolean = true;

  constructor() { }

  ngOnInit() {
  }

  onChangedPageSize(pageSize: number): void {
    this.itemsPerPage = pageSize;
    console.log('VALIDATION DETAILS ' + pageSize)
  }

  onChangedFilterBy(searchBy: string): void {
    console.log('SEARCH '+ searchBy)
  }

  onSorted($event): void {
    this.sortBy = $event.sortColumn;
    this.sortDirection = $event.sortDirection;
  }
}
