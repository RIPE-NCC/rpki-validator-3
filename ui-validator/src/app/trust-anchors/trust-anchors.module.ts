import {NgModule} from '@angular/core';

import {TrustAnchorsComponent} from './trust-anchors.component';
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    TrustAnchorsComponent
  ],
  providers: []
})
export class TrustAnchorsModule {
}
