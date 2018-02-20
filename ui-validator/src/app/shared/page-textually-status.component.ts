import {Component, Input, OnInit} from '@angular/core';

@Component({
  selector: 'page-textually-status',
  template: `
    <label [hidden]='loading' class='col-form-label'>
      {{'SHOWING' | translate}}
      <span *ngIf='totalItems === 0'> 0 </span>
      <span *ngIf='totalItems > 0'> {{firstItem + 1}} {{'TO' | translate}} {{lastItem}} {{'OF' | translate}} {{totalItems}}</span>
      {{'ENTRIES' | translate}}
      <span *ngIf='totalItems != absolutItems'> {{'Roas.FILTERED_FROM' | translate}} {{absolutItems}} {{'Roas.TOTAL_ENTRIES' | translate}}</span>
    </label>
  `
})
export class PageTextuallyStatusComponent implements OnInit {

  @Input() loading: boolean;
  @Input() firstItem: number;
  @Input() lastItem: number;
  @Input() totalItems: number;
  @Input() absolutItems: number;

  constructor() { }

  ngOnInit() {}
}
