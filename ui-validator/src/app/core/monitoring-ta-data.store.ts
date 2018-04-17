import {Injectable} from '@angular/core';

import {ITrustAnchorOverview} from "../trust-anchors/trust-anchor.model";

@Injectable()
export class MonitoringTaDataStore {
  selectedTA: ITrustAnchorOverview;
}
