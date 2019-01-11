import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent implements OnInit {

  contextPath: string = "/";

  constructor() {
    this.contextPath = window['validator-context-path'];
  }

  ngOnInit() {
  }

}
