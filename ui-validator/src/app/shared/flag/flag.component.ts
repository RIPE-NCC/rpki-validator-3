import {Component, Input, OnChanges, OnInit} from '@angular/core';
import {isBoolean, isNumber} from "util";
import {isString} from "@ng-bootstrap/ng-bootstrap/util/util";

@Component({
  selector: 'flag',
  templateUrl: './flag.component.html',
  styleUrls: ['./flag.component.scss']
})
export class FlagComponent implements OnChanges {

  greenFlags: string[] = ['SUCCESS', 'VALID'];
  orangeFlags: string[] = ['WARNING', 'INVALID ASN'];
  redFlags: string[] = ['NOT VALID'];
  grayFlags: string[] = ['UNKNOWN'];

  @Input() value: string | number | boolean;
  @Input() color?: string;
  green: boolean = false;
  orange: boolean = false;
  red: boolean = false;
  gray: boolean = false;
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
    if (isString(this.value)) {
      const value: string = this.value.toUpperCase();

      if (this.greenFlags.indexOf(value) > -1) {
        this.green = true;
      } else if (this.orangeFlags.indexOf(value) > -1) {
        this.orange = true;
      } else if (this.redFlags.indexOf(value) > -1) {
        this.red = true;
      } else {
        this.gray = true;
      }
    }
  }

  setYesNoFlag(): void {
    const value = isString(this.value) ? this.value.toUpperCase() : this.value;
    if (value) {
      this.green = true;
      this.value = 'YES';

    } else {
      this.red = true;
      this.value = 'NO';
    }
  }

  removeAllFlags(): void {
    this.green = this.orange = this.red = this.gray = this.numericValue = false;
  }
}
