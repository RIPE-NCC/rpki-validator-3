import {browser, by, element} from 'protractor';
import {PaginatedTablePage} from "./PaginatedTablePage";

export class RoasPage extends PaginatedTablePage {

  constructor(q:string) {
    super();
    let uri = '/roas';
    if (q) {
      uri += '?q='+q;
    }
    browser.get(uri);
  }


  exportAsCsv() {
    element(by.cssContainingText('export a.btn-primary', 'CSV')).click();
    return this;
  }

  exportAsJson() {
    element(by.cssContainingText('export a.btn-primary', 'JSON')).click();
    return this;
  }


  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  expectExportButtonsVisible() {
    expect(element.all(by.css('export a.btn-primary')).count()).toBe(2);
    return this;
  }

}
