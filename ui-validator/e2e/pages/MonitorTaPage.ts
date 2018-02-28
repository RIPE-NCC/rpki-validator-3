import {browser, by, element} from 'protractor';

export class MonitorTaPage {

  constructor() {
    browser.get('/trust-anchors/monitor/3268');
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

}
