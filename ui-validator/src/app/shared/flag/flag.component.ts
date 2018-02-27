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
  numericValue: boolean = true;

  constructor() { }

  ngOnChanges() {
    this.removeAllFlags();
    if (isNumber(this.value)) {
      this.numericValue = true;
      this.setNumericFlag();
    } else if (isString(this.value)){
      this.setTextualFlag();
    } else {
      this.setYesNoFlag();
    }
  }

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
      case 'SUCCESS': {
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

  setYesNoFlag(): void {
    if (isBoolean(this.value)){
      if (this.value) {
        this.value = 'YES';
        this.green = true;
      } else {
        this.value = 'NO';
        this.red = true;
      }
    }
  }

  removeAllFlags(): void {
    this.green = this.orange = this.red = this.numericValue = false;
  }
}
