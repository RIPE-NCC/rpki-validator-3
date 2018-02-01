import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrustAnchorsComponent } from './trust-anchors.component';
import { TranslateModule } from '@ngx-translate/core';
import {TrustAnchorsService} from "./trust-anchors.service";

@NgModule({
  imports: [
    CommonModule,
    TranslateModule,
  ],
  declarations: [TrustAnchorsComponent],
  providers: [TrustAnchorsService]
})
export class TrustAnchorsModule { }
