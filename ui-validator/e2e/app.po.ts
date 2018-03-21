import {by, element} from 'protractor';
import {RoasPage} from './pages/RoasPage';
import {TrustAnchorsPage} from './pages/TrustAnchorsPage';
import {MonitorTaPage} from './pages/MonitorTaPage';
import {IgnoreFiltersPage} from './pages/IgnoreFiltersPage';
import {AnnouncementPreviewPage} from './pages/AnnouncementPreviewPage';
import {WhitelistPage} from './pages/WhitelistPage';

export class AppPage {

  navigateToRoas() {
    return new RoasPage();
  }

  navigateToTrustAnchors() {
    return new TrustAnchorsPage();
  }

  navigateToMonitorTa() {
    return new MonitorTaPage();
  }

  navigateToIgnoreFilters() {
    return new IgnoreFiltersPage();
  }

  navigateToWhitelist() {
    return new WhitelistPage();
  }

  navigateToAnnouncementPreview(asn: string, prefix: string) {
    return new AnnouncementPreviewPage(asn, prefix);
  }

  getParagraphText() {
    return element(by.css('app-root h1')).getText();
  }

  getElementByCss(byCss: string) {
    return element(by.css(byCss));
  }
}
