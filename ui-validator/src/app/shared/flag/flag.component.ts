import {Component, Input, OnInit} from '@angular/core';
import {isNumber} from "util";

@Component({
  selector: 'flag',
  templateUrl: './flag.component.html',
  styleUrls: ['./flag.component.scss']
})
export class FlagComponent implements OnInit {

  @Input() value: string | number;
  @Input() color?: string;
  green: boolean = false;
  orange: boolean = false;
  red: boolean = false;
  mutedColor: boolean = false;


  constructor() { }

  ngOnInit() {
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
      default: {
        this.red = true;
        break;
      }
    }
  }

  setTextualFlag(): void {
    switch(this.value) {
      case 'YES': {
        this.green = true;
        break;
      }
      case 'WARNING': {
        this.orange = true;
        break;
      }
      default: {
        this.red = true;
        break;
      }
    }
  }
}
