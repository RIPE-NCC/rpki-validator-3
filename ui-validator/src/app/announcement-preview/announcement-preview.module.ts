import {NgModule} from '@angular/core';

import {AnnouncementPreviewComponent} from './announcement-preview.component';
import {SharedModule} from "../shared/shared.module";
import {FormsModule} from "@angular/forms";

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    AnnouncementPreviewComponent
  ]
})
export class AnnouncementPreviewModule {
}
