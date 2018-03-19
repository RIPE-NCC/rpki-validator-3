import {browser, by, element} from 'protractor';
import {PaginatedTablePage} from "./PaginatedTablePage";

export class WhitelistPage extends PaginatedTablePage {

  constructor() {
    super();
    browser.get('/whitelist');
  }

  addWhitelistEntry(prefix: string = "", asn: string = "") {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form button')).click();
    return this;
  }

  addWhitelistEntry(prefix: string = "", asn: string = "", maxLength: number) {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form #maxLength')).sendKeys(maxLength);
    element(by.css('form button')).click();
    return this;
  }

  addWhitelistEntry(prefix: string = "", asn: string = "", maxLength: number = 0, comment: string = "") {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form #maxLength')).sendKeys(maxLength);
    element(by.css('form #comment')).sendKeys(comment);
    element(by.css('form button')).click();
    return this;
  }

  clearEntryForm() {
    element(by.css('form #prefix')).clear();
    element(by.css('form #asn')).clear();
    element(by.css('form #maxLength')).clear();
    element(by.css('form #comment')).clear();
    return this;
  }

  deleteEntry(row: number) {
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

  expectMaxLengthValidationMessage() {
    expect(element(by.css('form em')).getText()).toBe('Number must be between 12 and 32');
    return this;
  }

  expectPrefixAndAsnValidationMessage() {
    browser.actions().mouseMove(element(by.css('form button'))).perform();
    expect(element(by.css('form em')).getText()).toBe('Prefix and ASN are required');
    return this;
  }

  expectEntryAdded() {
    expect(element(by.css('.alert.alert-success')).getText()).toContain('The entry has been added to the whitelist.');
    return this;
  }

}
