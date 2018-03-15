import {by, element} from "protractor";

export abstract class PaginatedTablePage {

  search(text: string) {
    element(by.css('search-by input')).sendKeys(text);
    return this;
  }

  changePaginationSizeTo(size: number) {
    element(by.cssContainingText('page-size-select select option', size.toString())).click();
    return this;
  }

  goToPaginationPage(page: number) {
    element(by.cssContainingText('li.page-item a', page.toString())).click();
    return this;
  }

  goToNextPaginationPage() {
    element(by.css('li.page-item a[aria-label="Next"]')).click();
    return this;
  }

  goToPreviousPaginationPage() {
    element(by.css('li.page-item a[aria-label="Previous"]')).click();
    return this;
  }

  goToFirstPaginationPage() {
    element(by.css('li.page-item a[aria-label="First"]')).click();
    return this;
  }

  goToLastPaginationPage() {
    element(by.css('li.page-item a[aria-label="Last"]')).click();
    return this;
  }

  sortTableOn(col: string) {
    element(by.cssContainingText('table thead th', col)).click();
    return this;
  }

  expectRowColumnToBe(row: number, col: string, content: string) {
    element.all(by.css('table thead th')).each((el, i) => {
      el.getText().then(text => {
        if (text === col) {
          expect(element(by.css(`table tbody tr:nth-child(${row + 1}) td:nth-child(${i + 1})`)).getText()).toBe(content)
        }
      })
    });
    return this;
  }

  expectShowingEntriesToBe(text: string) {
    expect(element(by.css('page-textually-status label')).getText()).toEqual(text);
    return this;
  }

  expectPaginationSizeToBe(size: number) {
    expect(element(by.css('page-size-select select option:checked')).getText()).toEqual(size.toString());
    return this;
  }

  expectNumberOfTableRowsToBe(rows: number) {
    expect(element.all(by.css('table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectNextButtonsDisabled() {
    expect(element(by.css('li.page-item.disabled a[aria-label="Next"]')).isPresent()).toBe(true);
    expect(element(by.css('li.page-item.disabled a[aria-label="Last"]')).isPresent()).toBe(true);
    return this;
  }

  expectPreviousButtonsDisabled() {
    expect(element(by.css('li.page-item.disabled a[aria-label="Previous"]')).isPresent()).toBe(true);
    expect(element(by.css('li.page-item.disabled a[aria-label="First"]')).isPresent()).toBe(true);
    return this;
  }


}
