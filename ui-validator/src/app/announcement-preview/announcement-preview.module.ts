import { NgModule } from '@angular/core';
import { AnnouncementPreviewComponent } from './announcement-preview.component';
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    AnnouncementPreviewComponent
  ]
})
export class AnnouncementPreviewModule { }
