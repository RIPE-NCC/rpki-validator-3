import {browser, by, element} from 'protractor';
import {PaginatedTablePage} from "./PaginatedTablePage";

export class BgpPage extends PaginatedTablePage {

  constructor() {
    super();
    browser.get('/bgp-preview');
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  moveMouseOverRow(row: number) {
    browser.actions().mouseMove(element(by.css(`table tbody tr:nth-child(${row})`))).perform();
    return this;
  }

  expectTooltipOnRowHover(row: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row})[ng-reflect-ngb-tooltip]`)).isDisplayed()).toBe(true);
    return this;
  }

  expectTooltipTextToBe(row: number, tooltipText: string) {
    expect(element(by.css(`table tbody tr:nth-child(${row})`)).getAttribute('ng-reflect-ngb-tooltip')).toBe(tooltipText);
    return this;
  }

  clickOnRow(row: number) {
    element(by.css(`table tbody tr:nth-child(${row})`)).click();
    return this;
  }

  expectToOpenAnnouncementPage(asn: string, prefix: string) {
    expect(browser.getCurrentUrl()).toContain(`/announcement-preview?asn=AS56203&prefix=1.0.4.0%2F22`);
    return this;
  }

}
