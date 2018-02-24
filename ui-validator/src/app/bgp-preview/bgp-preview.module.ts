import {NgModule} from '@angular/core';

import {SharedModule} from "../shared/shared.module";
import {BgpPreviewComponent} from "./bgp-preview.component";
import {BgpService} from "./bgp.service";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    BgpPreviewComponent
  ],
  providers: [
    BgpService
  ]
})
export class BgpPreviewModule {
}
