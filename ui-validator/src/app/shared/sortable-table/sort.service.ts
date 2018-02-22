import { Injectable } from '@angular/core';
import { Subject } from 'rxjs/Subject';

export interface ColumnSortedEvent {
  sortColumn: string;
  sortDirection: string;
}

@Injectable()
export class SortService {

  constructor() { }

  private columnSortedSource = new Subject<ColumnSortedEvent>();

  columnSorted = this.columnSortedSource.asObservable();

  sortColumn(event: ColumnSortedEvent) {
    this.columnSortedSource.next(event);
  }

}
