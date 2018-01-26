import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrustAnchorsComponent } from './trust-anchors.component';
import {RouterModule} from '@angular/router';

@NgModule({
  imports: [
    CommonModule,
    RouterModule.forChild([
      { path: 'trust-anchors', component: TrustAnchorsComponent}
    ]),
  ],
  declarations: [TrustAnchorsComponent]
})
export class TrustAnchorsModule { }
