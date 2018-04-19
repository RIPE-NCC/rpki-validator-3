import {browser, by, element} from 'protractor';
import {PaginatedTablePage} from './PaginatedTablePage';

export class WhitelistPage extends PaginatedTablePage {

  constructor() {
    super();
    browser.get('/whitelist');
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  addWhitelistEntry(prefix: string = '', asn: string = '') {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form .align-self-end button')).click();
    return this;
  }

  addWhitelistEntry(prefix: string = '', asn: string = '', maxLength: number) {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form #maxLength')).sendKeys(maxLength);
    element(by.css('form .align-self-end button')).click();
    return this;
  }

  addWhitelistEntry(prefix: string = '', asn: string = '', maxLength: number = 0, comment: string = '') {
    element(by.css('form #prefix')).sendKeys(prefix);
    element(by.css('form #asn')).sendKeys(asn);
    element(by.css('form #maxLength')).sendKeys(maxLength);
    element(by.css('form #comment')).sendKeys(comment);
    element(by.css('form .align-self-end button')).click();
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

  expectPrefixAndAsnValidationMessage() {
    browser.actions().mouseMove(element(by.css('form .align-self-end button'))).perform();
    expect(element(by.css('#toast-container .toast-info')).isPresent()).toBe(true);
    expect(element(by.id('toast-container')).getText()).toBe('Prefix and ASN are required to enable adding.');
    return this;
  }

  expectEntryAdded() {
    expect(element(by.css('#toast-container .toast-success')).isPresent()).toBe(true);
    expect(element(by.id('toast-container')).getText()).toContain('The entry has been added to the whitelist.');
    return this;
  }

  moveMouseOverColumn(row: number, column: number) {
    browser.actions().mouseMove(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) span`))).perform();
    return this;
  }

  expectNumberOfAnnouncments(row: number, column: number, numberAnnouncments: string) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) span`)).getText()).toBe(numberAnnouncments);
    return this;
  }

  expectPopoverToHave(row: number, column: number, expectToSee: boolean) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) popover-entry`)).isPresent()).toBe(expectToSee);
    return this;
  }

  expectPopoverOnHover(row: number, column: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) popover-entry`)).isDisplayed()).toBe(true);
    return this;
  }

  moveMouseOverRowInPopover(row: number, column: number) {
    browser.actions().mouseMove(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) popover-entry table tbody tr:nth-child(1)`))).perform();
    return this;
  }

  expectTooltipTextToBe(row: number, column: number, tooltipText: string) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) popover-entry table tbody tr:nth-child(1)`)).getAttribute('ng-reflect-ngb-tooltip')).toContain(tooltipText);
    return this;
  }

  clickOnRowInPopoverOfSpecifiedCell(row: number, column: number) {
    element(by.css(`table tbody tr:nth-child(${row}) td:nth-child(${column}) popover-entry table tbody tr:nth-child(1)`)).click();
    return this;
  }

  expectToOpenAnnouncementPage(asn: string, prefix: string) {
    expect(browser.getCurrentUrl()).toContain(`/announcement-preview?asn=AS56203&prefix=1.0.6.0%2F24`);
    return this;
  }
}
