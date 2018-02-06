import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrustAnchorsComponent } from './trust-anchors.component';
import { TranslateModule } from '@ngx-translate/core';
import {TrustAnchorsService} from "./trust-anchors.service";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

@NgModule({
  imports: [
    CommonModule,
    TranslateModule,
    NgbModule
  ],
  declarations: [TrustAnchorsComponent],
  providers: [TrustAnchorsService]
})
export class TrustAnchorsModule { }
