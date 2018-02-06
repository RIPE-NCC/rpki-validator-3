import {NgModule} from '@angular/core';

import {HomeComponent} from './home.component';
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    HomeComponent
  ]
})
export class HomeModule {
}
