import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

@Component({
  selector: 'search-by',
  template: `
    <div class='d-inline-flex ml-auto'>
      <label class='col-form-label mr-1'>{{ 'SEARCH' | translate }}:</label>
      <input type='text'
             class='form-control'
             placeholder='{{ "SEARCH" | translate }}'
             [(ngModel)]='searchBy'
             (keyup)='onFilter()'/>
    </div>
  `
})
export class SearchComponent implements OnInit {

  @Input() searchBy: string;
  @Output() searchByChanged: EventEmitter<string> = new EventEmitter<string>();

  constructor() { }

  ngOnInit() {
  }
  timer: any;

  onFilter() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {this.searchByChanged.emit(this.searchBy) },500)
  }
}
