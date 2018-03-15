import {browser, by, element} from 'protractor';
import {PaginatedTablePage} from "./PaginatedTablePage";

export class IgnoreFiltersPage extends PaginatedTablePage {

  constructor() {
    super();
    browser.get('/filters');
  }

  addFilter(prefix: string = "", asn: string = "", comment: string = "") {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form #comment')).sendKeys(comment);
    element(by.css('form button')).click();
    return this;
  }

  clearFilterForm() {
    element(by.css('form #prefix')).clear();
    element(by.css('form #asn')).clear();
    element(by.css('form #comment')).clear();
    return this;
  }

  deleteFilter(row: number) {
    element(by.css(`table tbody tr:nth-child(${row}) td .ban-icon`)).click();
    return this;
  }

  expectPrefixValidationMessage() {
    expect(element(by.css('form em')).getText()).toBe('Not valid prefix');
    return this;
  }

  expectAsnValidationMessage() {
    expect(element(by.css('form em')).getText()).toBe('Not valid asn');
    return this;
  }

  expectPrefixAndAsnValidationMessage() {
    browser.actions().mouseMove(element(by.css('form button'))).perform();
    expect(element(by.css('form em')).getText()).toBe('Prefix and/or ASN are required');
    return this;
  }

  expectFilterAdded() {
    expect(element(by.css('.alert.alert-success')).getText()).toContain('The filter has been added.');
    return this;
  }

}
