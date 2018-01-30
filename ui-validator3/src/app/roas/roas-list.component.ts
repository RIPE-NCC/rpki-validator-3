import { Component, OnInit } from '@angular/core';
import {RoasService} from "./roas.service";
import {IRoa} from "./roa";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent implements OnInit {
  alertShown = true;
  errorMessage: string;
  roas: IRoa[] = [];
  // pagination
  roasPerPage: number;
  totalRoas: number;
  page: IRoa[];
  previousPage: any;
  showPages: IRoa[] = [];

  constructor(private _roasService: RoasService) { }

  ngOnInit() {
      this.loadData();
  }

  loadPage(page: number) {
      if (page !== this.previousPage) {
          this.previousPage = page;
          this.loadData();
      }
  }

  loadData() {
    if (this.roas.length > 0) {

    } else {
      this._roasService.getRoas()
          .subscribe(response => {
                  this.roas = response.data,
                  this.totalRoas = this.roas.length},
              error => this.errorMessage = <any>error);
    }
  }

    // loadData() {
    //     this._roasService.getRoas({
    //         page: this.page - 1,
    //         size: this.itemsPerPage
    //     }).subscribe(
    //         (res: Response) => {
    //               this.roas = response.data,
    //               this.totalRoas = this.roas.length},
    //         error => this.errorMessage = <any>error);
    // }
}
