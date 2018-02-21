import {Component, Input, OnChanges, OnInit} from '@angular/core';
import {isBoolean, isNumber} from "util";
import {isString} from "@ng-bootstrap/ng-bootstrap/util/util";

@Component({
  selector: 'flag',
  templateUrl: './flag.component.html',
  styleUrls: ['./flag.component.scss']
})
export class FlagComponent implements OnChanges {

  @Input() value: string | number | boolean;
  @Input() color?: string;
  green: boolean = false;
  orange: boolean = false;
  red: boolean = false;
  mutedColor: boolean = false;


  constructor() { }

  ngOnChanges() {
    if (isNumber(this.value)) {
      this.setNumericFlag();
    } else {
      this.setTextualFlag();
    }
  }

  //FIXME switch have to be changes in both methods - depend of response from backend
  setNumericFlag(): void {
    this.mutedColor = this.value <= 0;
    switch(this.color) {
      case 'green': {
        this.green = true;
        break;
      }
      case 'orange': {
        this.orange = true;
        break;
      }
      case 'red': {
        this.red = true;
        break;
      }
    }
  }

  setTextualFlag(): void {
    const value = isString(this.value) ? this.value.toUpperCase() : this.value;
    switch(value) {
      case true: {
        this.green = true;
        break;
      }
      case 'WARNING': {
        this.orange = true;
        break;
      }
      case 'ERROR': {
        this.red = true;
        break;
      }
    }
  }
}
