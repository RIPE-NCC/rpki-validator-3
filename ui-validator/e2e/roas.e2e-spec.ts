import {AppPage} from './app.po';
import {RoasPage} from "./pages/RoasPage";

describe('Roas', () => {
  let roasPage: RoasPage;

  beforeEach(() => {
    const page = new AppPage();
    roasPage = page.navigateToRoas()
  });

  it('should display correctly Validated ROAs page', () => {
    roasPage
      .expectTitleHeaderToBe('Validated ROAs')
      .expectPaginationSizeToBe(10)
      .expectExportButtonsVisible();

  });

  describe('Paginated table', () => {

    it('should display pagination when there are enough rows', () => {
      // default pagination
      roasPage
        .expectPreviousButtonsDisabled()
        .expectPaginationSizeToBe(10)
        .expectNumberOfTableRowsToBe(10)
        .expectShowingEntriesToBe('Showing 1 to 10 of 40 entries');

      // change pagination to 25
      roasPage
        .changePaginationSizeTo(25)
        .expectPaginationSizeToBe(25)
        .expectNumberOfTableRowsToBe(25)
        .expectShowingEntriesToBe('Showing 1 to 25 of 40 entries')
        .goToNextPaginationPage()
        .expectShowingEntriesToBe('Showing 26 to 40 of 40 entries')
        .expectNextButtonsDisabled()
        .goToFirstPaginationPage()
        .expectPreviousButtonsDisabled()
        .expectShowingEntriesToBe('Showing 1 to 25 of 40 entries')

    });

    it('should not display pagination when there are not enough rows', () => {
      roasPage
        .changePaginationSizeTo(50)
        .expectPaginationSizeToBe(50)
        .expectNumberOfTableRowsToBe(40)
        .expectShowingEntriesToBe('Showing 1 to 40 of 40 entries')
        .expectNextButtonsDisabled()
        .expectPreviousButtonsDisabled()
    });

    it('should order table rows when clicking on table headers', () => {
      roasPage
        .sortTableOn('ASN')
        .expectRowColumnToBe(0, 'ASN', '37146')
        .sortTableOn('ASN')
        .expectRowColumnToBe(0, 'ASN', '30844');

      roasPage
        .sortTableOn('Prefix')
        .expectRowColumnToBe(0, 'Prefix', '41.60.0.0/20')
        .sortTableOn('Prefix')
        .expectRowColumnToBe(0, 'Prefix', '10.60.0.0/20');

      roasPage
        .sortTableOn('Trust Anchors')
        .expectRowColumnToBe(0, 'Trust Anchors', 'AfriNIC RPKI Root')
        .sortTableOn('Trust Anchors')
        .expectRowColumnToBe(0, 'Trust Anchors', 'ZfriNIC RPKI Root');

    });

    it('should filter content table when using filter', () => {
        roasPage
          .search('Bobo')
          .expectNumberOfTableRowsToBe(2)
          .expectShowingEntriesToBe('Showing 1 to 2 of 2 entries (filtered from 40 total entries)');
    });

  });

  describe('export', () => {

    it('as CSV', () => {
      roasPage
        .expectExportButtonsVisible()
        .exportAsCsv();
    });

    it('as JSON', () => {
      roasPage
        .expectExportButtonsVisible()
        .exportAsJson();
    });
  })
});
