import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

@Component({
  selector: 'search-by',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.scss']
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
    this.timer = setTimeout(() => {this.searchByChanged.emit(this.searchBy) },300)
  }
}
