import {AppPage} from './app.po';
import {IgnoreFiltersPage} from "./pages/IgnoreFiltersPage";

describe('Ignore Filters Page', () => {
  let ignoreFiltersPage: IgnoreFiltersPage;

  beforeEach(() => {
    const page = new AppPage();
    ignoreFiltersPage = page.navigateToIgnoreFilters();
  });

  describe('add filter', () => {
    it('should show validation error if prefix and asn are empty', () => {
      ignoreFiltersPage
        .clearFilterForm()
        .expectPrefixAndAsnValidationMessage();
    });

    it('should add a filter with prefix', () => {
      ignoreFiltersPage
        .addFilter('NotValid')
        .expectPrefixValidationMessage()
        .clearFilterForm()
        .addFilter('1.0.128.0/20')
        .expectFilterAdded();
    });

    it('should add a filter with asn', () => {
      ignoreFiltersPage
        .addFilter('', 'NotValid')
        .expectAsnValidationMessage()
        .clearFilterForm()
        .addFilter('', '11111')
        .expectFilterAdded();
    });

    it('should add a filter with prefix and comment', () => {
      ignoreFiltersPage
        .addFilter('1.0.128.0/20', '', 'comment')
        .expectFilterAdded();

    });

    it('should add a filter with asn and comment', () => {
      ignoreFiltersPage
        .addFilter('', '11111', 'comment')
        .expectFilterAdded();

    });

    it('should add a filter with prefix, asn and comment', () => {
      ignoreFiltersPage
        .addFilter('1.0.128.0/20', '11111', 'comment')
        .expectFilterAdded();

    });
  });

  describe('current filters', () => {
    it('should show filter on table', () => {
      ignoreFiltersPage
        .expectNumberOfTableRowsToBe(6)
        .expectRowColumnToBe(1, 'Prefix', '1.0.128.0/18')
    });

    it('should delete filter on table', () => {
      ignoreFiltersPage
        .expectNumberOfTableRowsToBe(6)
        .deleteFilter(1)
    });

  });

});
