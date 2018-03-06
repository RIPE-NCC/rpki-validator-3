import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnnouncementPreviewComponent } from './announcement-preview.component';
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [AnnouncementPreviewComponent]
})
export class AnnouncementPreviewModule { }
