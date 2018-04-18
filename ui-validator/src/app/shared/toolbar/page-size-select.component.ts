import {Component, EventEmitter, OnInit, Output} from '@angular/core';

@Component({
  selector: 'page-size-select',
  template: `
    <div class='d-inline-flex'>
      <label class='col-form-label mr-1'>{{ 'SHOW' | translate }} </label>
      <select #selectPageSize (change)='onChangePageSize(selectPageSize.value)' class='custom-select'>
        <option *ngFor='let size of pageSizes' [value]='size'>{{size}}</option>
      </select>
      <label class='col-form-label ml-1'>{{ 'ENTRIES' | translate}}</label>
    </div>
  `
})
export class PageSizeSelectComponent implements OnInit {

  pageSizes: number[] = [10, 25, 50, 100];

  @Output() changedPageSize: EventEmitter<number> = new EventEmitter<number>();

  constructor() { }

  ngOnInit() {}

  onChangePageSize(size: number) {
    this.changedPageSize.emit(size);
  }
}
