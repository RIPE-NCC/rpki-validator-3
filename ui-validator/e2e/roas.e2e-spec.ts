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
