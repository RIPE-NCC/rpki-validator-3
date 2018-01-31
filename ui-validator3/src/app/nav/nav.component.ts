import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-nav',
  templateUrl: './nav.component.html',
  styleUrls: ['./nav.component.scss']
})
export class NavComponent {
  pageTitle = 'Nav.TITLE_HOME';
  navbarCollapsed = true;

  constructor() { }

  private changeTitle(title: string) {
    this.pageTitle = title;
  }

}
