import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-nav',
  templateUrl: './nav.component.html',
  styleUrls: ['./nav.component.scss']
})
export class NavComponent implements OnInit {
  pageTitle = 'Nav.TITLE_HOME';

  constructor() { }

  ngOnInit() {
  }

  private changeTitle(title: string) {
    this.pageTitle = title;
  }
}
