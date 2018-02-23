import {Component, Input, OnInit, Output} from '@angular/core'
import {ActivatedRoute} from "@angular/router";

@Component({
  template: `
    <h1 class="errorMessage">{{errorMsg | translate}} {{error}}</h1>
  `,
  styles: [`
    .errorMessage { 
      margin:200px 0; 
      font-size: 36px;
      text-align: center; 
    }`]
})
export class ErrorComponent implements OnInit {

  errorMsg: string = 'Page not found';
  error: string = '404';

  constructor(private _activatedRoute: ActivatedRoute) { }

  ngOnInit(): void {
    const errorStatus = this._activatedRoute.snapshot.params['error'];
    if (errorStatus && errorStatus !== '200') {
      this.error = errorStatus;
      this.errorMsg = this._activatedRoute.snapshot.params['errorMsg'];
    }
  }
}
