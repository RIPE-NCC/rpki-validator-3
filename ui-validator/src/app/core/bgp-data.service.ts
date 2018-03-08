import { Injectable } from '@angular/core';
import {IBgp} from "../bgp-preview/bgp.model";

@Injectable()
export class BgpDataService {
  bgpData: IBgp;
}
