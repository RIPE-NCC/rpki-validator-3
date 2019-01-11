import {AppPage} from './app.po';
import {RoasPage} from "./pages/RoasPage";

describe('Roas Page', () => {
  let roasPage: RoasPage;
  const page = new AppPage();

  beforeEach(() => {
    roasPage = page.navigateToRoas('')
  });

  it('should display correctly Validated ROAs page', () => {
    roasPage
      .expectTitleHeaderToBe('Validated ROAs')
      .expectPaginationSizeToBe(10)
      .expectExportButtonsVisible();

  });

  it('should search directly when using query param', () => {
    page.navigateToRoas('Bobo')
      .expectNumberOfTableRowsToBe(2)
      .expectShowingEntriesToBe('Showing 1 to 2 of 2 entries');
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
