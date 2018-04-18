import {NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';

import {AnnouncementPreviewComponent} from './announcement-preview.component';
import {SharedModule} from '../shared/shared.module';

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
