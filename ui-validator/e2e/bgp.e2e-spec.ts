import {AppPage} from './app.po';
import {BgpPage} from "./pages/BgpPage";

describe('Bgp-preview Page', () => {
  let bgpPage: BgpPage;

  beforeEach(() => {
    const page = new AppPage();
    bgpPage = page.navigateToBgp()
  });

  it('should display correctly BGP Preview page', () => {
    bgpPage
      .expectTitleHeaderToBe('BGP Preview')
      .expectPaginationSizeToBe(10);
  });

  it('should navigate to Announcement Page on click on row', () => {
    bgpPage
      .moveMouseOverRow(0)
      .expectTooltipOnRowHover(0)
      .expectTooltipTextToBe(0, 'Click to view announcement pre')
      .clickOnRow(0)
      .expectToOpenAnnouncementPage('AS56203','1.0.4.0/22');
  });
});
