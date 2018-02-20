import { Component, OnInit } from '@angular/core';
import {IValidationDetail} from "./validation-detail";
import {ManagingTable} from "../../shared/managing-table";

@Component({
  selector: 'validation-details',
  templateUrl: './validation-details.component.html',
  styleUrls: ['./validation-details.component.scss']
})
export class ValidationDetailsComponent extends ManagingTable implements OnInit {

  validationDetails: IValidationDetail[] = [];

  constructor() {
    super();
  }

  ngOnInit() {
  }

  loadData() {
  }
}
