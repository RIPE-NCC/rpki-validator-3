import { Component, OnInit } from '@angular/core';
import {RoasService} from "./roas.service";
import {IRoa} from "./roa";
import {IRoasRespons} from "./roas-respons";

@Component({
  selector: 'app-roas',
  templateUrl: './roas-list.component.html',
  styleUrls: ['./roas-list.component.scss']
})
export class RoasListComponent implements OnInit {
  errorMessage: string;
  roasResponse: IRoasRespons;
  roas: IRoa[] = [];
  constructor(private _roasService: RoasService) { }

  ngOnInit() {
      this._roasService.getRoas()
          .subscribe(roasResponse => {this.roasResponse = roasResponse,
              this.extractData(roasResponse.aaData)},
                      // this.filteredProducts = this.products;
              error => this.errorMessage = <any>error);
  }

    private extractData(dataRoas: string[]) {
        for (let data of dataRoas) {
          const roa = {
                  asn: data[0],
                  prefix: data[1],
                  maxLenght: data[2],
                  trustAnchor: data[3],
          }
          this.roas.push(roa);
        }
    }
}
