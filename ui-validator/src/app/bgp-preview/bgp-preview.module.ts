import {NgModule} from '@angular/core';

import {SharedModule} from '../shared/shared.module';
import {BgpPreviewComponent} from './bgp-preview.component';

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    BgpPreviewComponent
  ],
  providers: []
})
export class BgpPreviewModule {
}
